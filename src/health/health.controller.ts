import {Controller, Get} from "@nestjs/common";

export interface ApiStatusResponse {
    ok: boolean;
}

@Controller("health")
export class HealthController {
    @Get()
    public show(): ApiStatusResponse {
        return {ok: true};
    }
}
