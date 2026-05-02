import * as fs from "node:fs/promises";
import * as path from "node:path";
import {AppConfigService} from "../../config/app-config.service";
import {ExecRunnerService, ProcessResult} from "./exec-runner.service";

class CapturingExecRunner extends ExecRunnerService {
    public outputPath: string | null = null;
    public workingDirectory: string | null = null;
    public prompt: string | null = null;

    protected override async executeCommand(command: string[], workingDirectory: string, prompt: string | null): Promise<ProcessResult> {
        this.workingDirectory = workingDirectory;
        this.outputPath = command[command.indexOf("--output-last-message") + 1];
        this.prompt = prompt;
        await fs.writeFile(this.outputPath, '{"text":"ok","suggested_replies":["a","b","c"]}');
        return {exitCode: 0, stdout: "", stderr: "", timedOut: false};
    }
}

describe("ExecRunnerService", () => {
    function config(): AppConfigService {
        return {codexExecTimeoutSeconds: 300, codexSandboxMode: "danger-full-access"} as AppConfigService;
    }

    it("uses temp directory as working directory", async () => {
        const execRunner = new CapturingExecRunner(config());

        const reply = await execRunner.run("prompt", [], {type: "object"});

        expect(reply).toBe('{"text":"ok","suggested_replies":["a","b","c"]}');
        expect(path.dirname(execRunner.outputPath!)).toBe(execRunner.workingDirectory);
        expect(path.resolve(execRunner.workingDirectory!)).not.toBe(path.resolve("."));
    });

    it("wraps system and user prompts", async () => {
        const execRunner = new CapturingExecRunner(config());

        await execRunner.run("system rules", "user payload", [], {type: "object"});

        expect(execRunner.prompt).toContain("<system_prompt>");
        expect(execRunner.prompt).toContain("system rules");
        expect(execRunner.prompt).toContain("<user_prompt>");
        expect(execRunner.prompt).toContain("user payload");
    });
});
