require "open3"
require "tmpdir"

class Documents::PdfPageRasterizer
  DEFAULT_MAX_PAGES = 4
  RasterizationError = Class.new(StandardError)
  MissingDependencyError = Class.new(RasterizationError)

  def initialize(max_pages: DEFAULT_MAX_PAGES)
    @max_pages = max_pages
  end

  def rasterize(pdf_path)
    raise RasterizationError, "pdf path is missing" if pdf_path.to_s.strip.empty?
    raise MissingDependencyError, "pdftoppm is not installed" unless pdftoppm_available?

    output_prefix = File.join(File.dirname(pdf_path), "pdf-page")
    command = [
      "pdftoppm",
      "-f", "1",
      "-l", max_pages.to_s,
      "-png",
      pdf_path,
      output_prefix
    ]
    _stdout, stderr, status = Open3.capture3(*command)
    raise RasterizationError, "pdftoppm failed: #{stderr.strip.presence || 'unknown error'}" unless status.success?

    image_paths = Dir.glob("#{output_prefix}-*.png").sort
    raise RasterizationError, "pdf conversion did not produce any page images" if image_paths.empty?

    image_paths
  end

  private

  attr_reader :max_pages

  def pdftoppm_available?
    system("which", "pdftoppm", out: File::NULL, err: File::NULL)
  end
end
