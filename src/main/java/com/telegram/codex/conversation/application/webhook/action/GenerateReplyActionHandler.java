package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.application.job.JobSchedulerService;
import com.telegram.codex.conversation.domain.Decision;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GenerateReplyActionHandler implements ActionHandler {

    private final JobSchedulerService jobSchedulerService;

    public GenerateReplyActionHandler(JobSchedulerService jobSchedulerService) {
        this.jobSchedulerService = jobSchedulerService;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.GENERATE_REPLY;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        jobSchedulerService.enqueueReplyGeneration(decision.message());
    }
}
