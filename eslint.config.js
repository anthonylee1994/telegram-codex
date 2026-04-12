import js from "@eslint/js";
import eslintConfigPrettier from "eslint-config-prettier";
import globals from "globals";
import tseslint from "typescript-eslint";

const localRules = {
    "type-imports-last": {
        meta: {
            type: "suggestion",
            docs: {
                description: "Require type-only imports to appear after value imports",
            },
            schema: [],
        },
        create(context) {
            return {
                Program(node) {
                    let seenTypeImport = false;

                    for (const statement of node.body) {
                        if (statement.type !== "ImportDeclaration") {
                            continue;
                        }

                        if (statement.importKind === "type") {
                            seenTypeImport = true;
                            continue;
                        }

                        if (seenTypeImport) {
                            context.report({
                                node: statement,
                                message: "Value imports must appear before `import type` declarations.",
                            });
                        }
                    }
                },
            };
        },
    },
};

export default tseslint.config(
    {
        ignores: ["dist/**", "coverage/**", "node_modules/**"],
    },
    js.configs.recommended,
    ...tseslint.configs.recommended,
    {
        files: ["**/*.ts"],
        languageOptions: {
            ecmaVersion: 2024,
            globals: {
                ...globals.node,
            },
            sourceType: "module",
        },
        plugins: {
            local: {
                rules: localRules,
            },
        },
        rules: {
            "local/type-imports-last": "error",
        },
    },
    eslintConfigPrettier
);
