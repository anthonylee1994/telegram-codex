require "cgi"
require "open3"

class Documents::TextDocumentExtractor
  DEFAULT_MAX_BYTES = 200_000
  DEFAULT_MAX_CHARS = 12_000

  ExtractionResult = Struct.new(:content, :truncated, keyword_init: true)
  MissingDependencyError = Class.new(StandardError)

  def initialize(max_bytes: DEFAULT_MAX_BYTES, max_chars: DEFAULT_MAX_CHARS)
    @max_bytes = max_bytes
    @max_chars = max_chars
  end

  def extract(file_path)
    raw_content = extract_raw_content(file_path)
    truncated_by_bytes = raw_content.bytesize > max_bytes
    raw_content = raw_content.byteslice(0, max_bytes) if truncated_by_bytes

    normalized_content = raw_content.force_encoding(Encoding::UTF_8).encode(
      Encoding::UTF_8,
      invalid: :replace,
      undef: :replace,
      replace: ""
    ).strip
    truncated_by_chars = normalized_content.length > max_chars
    normalized_content = normalized_content[0, max_chars].to_s.rstrip if truncated_by_chars

    ExtractionResult.new(
      content: normalized_content,
      truncated: truncated_by_bytes || truncated_by_chars
    )
  end

  private

  attr_reader :max_bytes, :max_chars

  def extract_raw_content(file_path)
    case File.extname(file_path).downcase
    when ".docx"
      extract_docx_content(file_path)
    when ".xlsx"
      extract_xlsx_content(file_path)
    else
      File.binread(file_path, max_bytes + 1)
    end
  end

  def extract_docx_content(file_path)
    document_xml = unzip_file_content(file_path, "word/document.xml")
    document = Nokogiri::XML(document_xml)
    paragraphs = document.xpath("//*[local-name()='p']").filter_map do |paragraph|
      text = paragraph.xpath(".//*[local-name()='t']").map(&:text).join.strip
      next if text.empty?

      text
    end

    paragraphs.join("\n")
  end

  def extract_xlsx_content(file_path)
    shared_strings = load_xlsx_shared_strings(file_path)
    worksheet_entries = unzip_list(file_path).grep(%r{\Axl/worksheets/sheet\d+\.xml\z}).sort
    worksheet_entries.map.with_index(1) do |worksheet_entry, index|
      sheet_content = extract_xlsx_sheet(file_path, worksheet_entry, shared_strings)
      next if sheet_content.empty?

      ["[Sheet #{index}]", sheet_content].join("\n")
    end.compact.join("\n\n")
  end

  def load_xlsx_shared_strings(file_path)
    entries = unzip_list(file_path)
    return [] unless entries.include?("xl/sharedStrings.xml")

    shared_strings_xml = unzip_file_content(file_path, "xl/sharedStrings.xml")
    Nokogiri::XML(shared_strings_xml).xpath("//*[local-name()='si']").map do |item|
      item.xpath(".//*[local-name()='t']").map(&:text).join
    end
  end

  def extract_xlsx_sheet(file_path, worksheet_entry, shared_strings)
    worksheet_xml = unzip_file_content(file_path, worksheet_entry)
    worksheet = Nokogiri::XML(worksheet_xml)

    worksheet.xpath("//*[local-name()='sheetData']/*[local-name()='row']").filter_map do |row|
      values = row.xpath("./*[local-name()='c']").map do |cell|
        extract_xlsx_cell_value(cell, shared_strings)
      end.map(&:strip).reject(&:empty?)
      next if values.empty?

      values.join("\t")
    end.join("\n")
  end

  def extract_xlsx_cell_value(cell, shared_strings)
    cell_type = cell["t"].to_s
    return cell.xpath(".//*[local-name()='t']").map(&:text).join if cell_type == "inlineStr"

    value = cell.at_xpath("./*[local-name()='v']")&.text.to_s
    return shared_strings[value.to_i].to_s if cell_type == "s"

    value
  end

  def unzip_list(file_path)
    ensure_unzip_available!

    stdout, stderr, status = Open3.capture3("unzip", "-Z1", file_path)
    raise "unzip failed: #{stderr.strip.presence || 'unknown error'}" unless status.success?

    stdout.lines.map(&:strip)
  end

  def unzip_file_content(file_path, entry_path)
    ensure_unzip_available!

    stdout, stderr, status = Open3.capture3("unzip", "-p", file_path, entry_path)
    raise "unzip failed: #{stderr.strip.presence || 'unknown error'}" unless status.success?

    stdout
  end

  def ensure_unzip_available!
    return if system("which", "unzip", out: File::NULL, err: File::NULL)

    raise MissingDependencyError, "unzip is not installed"
  end
end
