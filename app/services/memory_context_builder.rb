class MemoryContextBuilder
  def build(memories)
    return nil if memories.empty?

    lines = memories.map do |memory|
      "#{memory.kind}: #{memory.key} = #{memory.value}"
    end

    [
      "已知用戶記憶：",
      *lines
    ].join("\n")
  end
end
