package org.finos.fluxnova.bpm.agentic.poc;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PocController {

    private final RuntimeService runtimeService;

    public PocController(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @PostMapping("/poc/start")
    public Map<String, Object> start(@RequestParam(name = "applicantMessage", required = false) String applicantMessage,
                                     @RequestParam(name = "applicantId", required = false) String applicantId,
                                     @RequestParam(name = "loanAmount", required = false) Integer loanAmount,
                                     @RequestParam(name = "annualIncome", required = false) Integer annualIncome,
                                     @RequestParam(name = "creditScore", required = false) Integer creditScore,
                                     @RequestParam(name = "loanPurpose", required = false) String loanPurpose,
                                     @RequestParam(name = "userQuestion", required = false) String userQuestion,
                                     @RequestParam(name = "customerId", required = false) String customerId,
                                     @RequestParam(name = "applicationAmount", required = false) Integer applicationAmount,
                                     @RequestParam(name = "maxTurns", required = false) Integer maxTurns) {

        Map<String, Object> vars = new LinkedHashMap<>();
        String resolvedApplicantMessage = applicantMessage != null ? applicantMessage : userQuestion;
        String resolvedApplicantId = applicantId != null ? applicantId : customerId;
        Integer resolvedLoanAmount = loanAmount != null ? loanAmount : applicationAmount;

        if (resolvedApplicantMessage != null) vars.put("applicantMessage", resolvedApplicantMessage);
        if (resolvedApplicantId != null) vars.put("applicantId", resolvedApplicantId);
        if (resolvedLoanAmount != null) vars.put("loanAmount", resolvedLoanAmount);
        if (annualIncome != null) vars.put("annualIncome", annualIncome);
        if (creditScore != null) vars.put("creditScore", creditScore);
        if (loanPurpose != null) vars.put("loanPurpose", loanPurpose);
        if (maxTurns != null) vars.put("_agent.maxTurns", maxTurns);

        Map<String, Object> out = new LinkedHashMap<>();
        try {
            ProcessInstance pi = runtimeService.startProcessInstanceByKey("agenticPoc", vars);
            out.put("processInstanceId", pi.getId());
            out.put("processDefinitionId", pi.getProcessDefinitionId());
            out.put("status", "STARTED");
        } catch (Exception e) {
            out.put("status", "ERROR");
            out.put("error", e.getClass().getName());
            out.put("message", e.getMessage());
        }
        return out;
    }
}
