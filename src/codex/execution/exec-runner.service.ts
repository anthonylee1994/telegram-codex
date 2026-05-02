import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import {spawn} from "node:child_process";
import {Injectable} from "@nestjs/common";
import {AppConfigService} from "../../config/app-config.service";

export class ExecutionException extends Error {}

export class ExecutionTimeoutException extends ExecutionException {}

export interface ProcessResult {
    exitCode: number;
    stdout: string;
    stderr: string;
    timedOut: boolean;
}

@Injectable()
export class ExecRunnerService {
    public constructor(private readonly config: AppConfigService) {}

    public async run(prompt: string | null | undefined, imageFilePaths: string[], outputSchema: unknown): Promise<string>;
    public async run(systemPrompt: string | null | undefined, userPrompt: string | null | undefined, imageFilePaths: string[], outputSchema: unknown): Promise<string>;
    public async run(...args: [string | null | undefined, string[], unknown] | [string | null | undefined, string | null | undefined, string[], unknown]): Promise<string> {
        const systemPrompt = args.length === 4 ? args[0] : null;
        const userPrompt = args.length === 4 ? args[1] : args[0];
        const imageFilePaths = args.length === 4 ? args[2] : args[1];
        const outputSchema = args.length === 4 ? args[3] : args[2];
        let tempDir: string | null = null;
        try {
            tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "telegram-codex-"));
            const outputPath = path.join(tempDir, "reply.txt");
            const schemaPath = outputSchema ? path.join(tempDir, "reply-schema.json") : null;
            if (schemaPath) {
                await fs.writeFile(schemaPath, JSON.stringify(outputSchema), "utf8");
            }
            const command = this.buildCommand(outputPath, schemaPath, imageFilePaths);
            const result = await this.executeCommand(command, tempDir, this.buildPrompt(systemPrompt, userPrompt), this.config.codexExecTimeoutSeconds);
            if (result.timedOut) {
                throw new ExecutionTimeoutException(`codex exec timed out after ${this.config.codexExecTimeoutSeconds} seconds`);
            }
            if (result.exitCode !== 0) {
                throw new ExecutionException(`codex exec failed: ${result.stderr.trim() || "unknown error"}`);
            }
            const replyText = (await fs.readFile(outputPath, "utf8")).trim();
            if (!replyText) {
                throw new ExecutionException("codex exec returned an empty reply");
            }
            return replyText;
        } catch (error) {
            if (error instanceof ExecutionException) {
                throw error;
            }
            throw new ExecutionException("Failed to run codex exec");
        } finally {
            if (tempDir) {
                await fs.rm(tempDir, {recursive: true, force: true}).catch(() => undefined);
            }
        }
    }

    protected executeCommand(command: string[], workingDirectory: string, prompt: string | null, timeoutSeconds: number): Promise<ProcessResult> {
        return new Promise((resolve, reject) => {
            const process = spawn(command[0], command.slice(1), {cwd: workingDirectory});
            let stdout = "";
            let stderr = "";
            let settled = false;
            const timeout = setTimeout(() => {
                settled = true;
                process.kill("SIGKILL");
                resolve({exitCode: -1, stdout, stderr, timedOut: true});
            }, timeoutSeconds * 1000);

            process.stdout.on("data", (chunk: Buffer) => {
                stdout += chunk.toString("utf8");
            });
            process.stderr.on("data", (chunk: Buffer) => {
                stderr += chunk.toString("utf8");
            });
            process.on("error", error => {
                clearTimeout(timeout);
                if (!settled) {
                    reject(error);
                }
            });
            process.on("close", code => {
                clearTimeout(timeout);
                if (!settled) {
                    resolve({exitCode: code ?? -1, stdout, stderr, timedOut: false});
                }
            });
            if (prompt !== null) {
                process.stdin.write(prompt, "utf8");
            }
            process.stdin.end();
        });
    }

    private buildPrompt(systemPrompt: string | null | undefined, userPrompt: string | null | undefined): string | null {
        if (!systemPrompt?.trim()) {
            return userPrompt ?? null;
        }
        if (!userPrompt?.trim()) {
            return systemPrompt;
        }
        return ["<system_prompt>", systemPrompt, "</system_prompt>", "<user_prompt>", userPrompt, "</user_prompt>"].join("\n\n");
    }

    private buildCommand(outputPath: string, schemaPath: string | null, imageFilePaths: string[]): string[] {
        const command = ["codex", "exec", "--skip-git-repo-check", "--sandbox", this.config.codexSandboxMode, "--color", "never", "--output-last-message", outputPath];
        if (schemaPath) {
            command.push("--output-schema", schemaPath);
        }
        for (const imageFilePath of imageFilePaths) {
            command.push("--image", imageFilePath);
        }
        command.push("-");
        return command;
    }
}
