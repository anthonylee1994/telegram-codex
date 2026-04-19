class TextDocumentExtractor
  DEFAULT_MAX_BYTES = 200_000
  DEFAULT_MAX_CHARS = 12_000

  ExtractionResult = Struct.new(:content, :truncated, keyword_init: true)

  def initialize(max_bytes: DEFAULT_MAX_BYTES, max_chars: DEFAULT_MAX_CHARS)
    @max_bytes = max_bytes
    @max_chars = max_chars
  end

  def extract(file_path)
    raw_content = File.binread(file_path, max_bytes + 1)
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
end
