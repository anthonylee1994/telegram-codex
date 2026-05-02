import {Module} from "@nestjs/common";
import {JsonPayloadParserService} from "./json-payload-parser.service";
import {ReplyParserService} from "./reply-parser.service";

@Module({
    providers: [JsonPayloadParserService, ReplyParserService],
    exports: [JsonPayloadParserService, ReplyParserService],
})
export class CodexParsingModule {}
