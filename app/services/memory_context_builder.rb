class MemoryContextBuilder
  def build(memories)
    return nil if memories.empty?

    lines = memories.map do |memory|
      "#{memory.kind}: #{memory.key} = #{memory.value}"
    end

    [
      "已知用戶記憶（只作背景參考；除非同最新訊息直接相關，否則唔好主動重複。如果同最新訊息有衝突，一律以最新訊息為準）：",
      *lines
    ].join("\n")
  end
end
