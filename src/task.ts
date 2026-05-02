import "reflect-metadata";
import {CommandFactory} from "nest-commander";
import {TaskModule} from "./task.module";

void CommandFactory.run(TaskModule, {logger: ["error", "warn", "log"]});
