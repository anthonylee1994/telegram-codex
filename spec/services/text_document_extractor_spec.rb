require "rails_helper"

RSpec.describe TextDocumentExtractor do
  let(:extractor) { described_class.new(max_bytes: 200_000, max_chars: 12_000) }
  let(:status) { instance_double(Process::Status, success?: true) }

  it "extracts plain text files directly" do
    allow(File).to receive(:binread).with("/tmp/notes.txt", 200_001).and_return("alpha\nbeta")

    result = extractor.extract("/tmp/notes.txt")

    expect(result.content).to eq("alpha\nbeta")
    expect(result.truncated).to eq(false)
  end

  it "extracts paragraph text from docx xml" do
    allow(extractor).to receive(:system).with("which", "unzip", out: File::NULL, err: File::NULL).and_return(true)
    allow(Open3).to receive(:capture3).with("unzip", "-p", "/tmp/notes.docx", "word/document.xml").and_return(
      ['<w:document xmlns:w="w"><w:body><w:p><w:r><w:t>第一段</w:t></w:r></w:p><w:p><w:r><w:t>第二段</w:t></w:r></w:p></w:body></w:document>', "", status]
    )

    result = extractor.extract("/tmp/notes.docx")

    expect(result.content).to eq("第一段\n第二段")
  end

  it "extracts worksheet rows from xlsx xml" do
    allow(extractor).to receive(:system).with("which", "unzip", out: File::NULL, err: File::NULL).and_return(true)
    allow(Open3).to receive(:capture3).with("unzip", "-Z1", "/tmp/sheet.xlsx").and_return(
      ["xl/sharedStrings.xml\nxl/worksheets/sheet1.xml\n", "", status]
    )
    allow(Open3).to receive(:capture3).with("unzip", "-p", "/tmp/sheet.xlsx", "xl/sharedStrings.xml").and_return(
      ['<sst><si><t>姓名</t></si><si><t>阿明</t></si></sst>', "", status]
    )
    allow(Open3).to receive(:capture3).with("unzip", "-p", "/tmp/sheet.xlsx", "xl/worksheets/sheet1.xml").and_return(
      ['<worksheet><sheetData><row><c t="s"><v>0</v></c><c><v>100</v></c></row><row><c t="s"><v>1</v></c><c><v>95</v></c></row></sheetData></worksheet>', "", status]
    )

    result = extractor.extract("/tmp/sheet.xlsx")

    expect(result.content).to include("[Sheet 1]")
    expect(result.content).to include("姓名\t100")
    expect(result.content).to include("阿明\t95")
  end

  it "raises a missing dependency error when unzip is unavailable for office files" do
    allow(extractor).to receive(:system).with("which", "unzip", out: File::NULL, err: File::NULL).and_return(false)

    expect {
      extractor.extract("/tmp/notes.docx")
    }.to raise_error(TextDocumentExtractor::MissingDependencyError, "unzip is not installed")
  end
end
