class UserMemoryFormatter
  def format(memories)
    return "而家未記住任何嘢。" if memories.empty?

    [
      "而家記住咗以下資料：",
      *memories.map { |memory| "- [#{memory.kind}] #{memory.key}: #{memory.value}" }
    ].join("\n")
  end
end
