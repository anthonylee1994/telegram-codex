import {Controller, Get} from "@nestjs/common";

@Controller("health")
export class HealthController {
    @Get()
    public getHealth(): {ok: true} {
        return {ok: true};
    }
}
