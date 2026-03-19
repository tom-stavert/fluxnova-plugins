package org.finos.fluxnova.ai.mcp.query.tools;

import org.finos.fluxnova.ai.mcp.query.model.dto.*;
import org.finos.fluxnova.ai.mcp.query.model.query.*;
import org.finos.fluxnova.bpm.engine.HistoryService;

import java.util.List;

/**
 * MCP tools for querying historic data from the process engine via {@link HistoryService}.
 */
public class HistoryQueryMcpTools {

    private final HistoryService historyService;
    private final int defaultMaxResults;

    public HistoryQueryMcpTools(
            HistoryService historyService,
            int defaultMaxResults) {
        this.historyService = historyService;
        this.defaultMaxResults = defaultMaxResults;
    }
    public List<HistoricProcessInstanceResultDto> queryHistoricProcessInstances(
                    HistoricProcessInstanceQueryDto queryDto) {
        var q = queryDto != null
                ? queryDto.toQuery(historyService)
                : historyService.createHistoricProcessInstanceQuery();
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricProcessInstanceResultDto::fromHistoricProcessInstance)
                .toList();
    }
    public List<HistoricActivityInstanceResultDto> queryHistoricActivityInstances(
                    HistoricActivityInstanceQueryDto queryDto) {
        var q = queryDto != null
                ? queryDto.toQuery(historyService)
                : historyService.createHistoricActivityInstanceQuery();
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricActivityInstanceResultDto::fromHistoricActivityInstance)
                .toList();
    }
    public List<HistoricTaskInstanceResultDto> queryHistoricTaskInstances(
                    HistoricTaskInstanceQueryDto queryDto) {
        var q = queryDto != null
                ? queryDto.toQuery(historyService)
                : historyService.createHistoricTaskInstanceQuery();
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricTaskInstanceResultDto::fromHistoricTaskInstance)
                .toList();
    }
    public List<HistoricDetailResultDto> queryHistoricDetails(
                    HistoricDetailQueryDto queryDto) {
        var q = queryDto != null
                ? queryDto.toQuery(historyService)
                : historyService.createHistoricDetailQuery();
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricDetailResultDto::fromHistoricDetail)
                .toList();
    }
    public List<HistoricVariableInstanceResultDto> queryHistoricVariableInstances(
                    HistoricVariableInstanceQueryDto queryDto) {
        var q = historyService.createHistoricVariableInstanceQuery();
        if (queryDto != null) queryDto.toQuery(q);
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricVariableInstanceResultDto::fromHistoricVariableInstance)
                .toList();
    }
    public List<UserOperationLogEntryResultDto> queryUserOperationLog(
                    UserOperationLogQueryDto queryDto) {
        var q = historyService.createUserOperationLogQuery();
        if (queryDto != null) queryDto.toQuery(q);
        return q.listPage(0, defaultMaxResults).stream()
                .map(UserOperationLogEntryResultDto::fromUserOperationLogEntry)
                .toList();
    }
    public List<HistoricIncidentResultDto> queryHistoricIncidents(
                    HistoricIncidentQueryDto queryDto) {
        var q = historyService.createHistoricIncidentQuery();
        if (queryDto != null) queryDto.toQuery(q);
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricIncidentResultDto::fromHistoricIncident)
                .toList();
    }
    public List<HistoricIdentityLinkLogResultDto> queryHistoricIdentityLinkLog(
                    HistoricIdentityLinkLogQueryDto queryDto) {
        var q = historyService.createHistoricIdentityLinkLogQuery();
        if (queryDto != null) queryDto.toQuery(q);
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricIdentityLinkLogResultDto::fromHistoricIdentityLinkLog)
                .toList();
    }
    public List<HistoricCaseInstanceResultDto> queryHistoricCaseInstances(
                    HistoricCaseInstanceQueryDto queryDto) {
        var q = historyService.createHistoricCaseInstanceQuery();
        if (queryDto != null) queryDto.toQuery(q);
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricCaseInstanceResultDto::fromHistoricCaseInstance)
                .toList();
    }
    public List<HistoricCaseActivityInstanceResultDto> queryHistoricCaseActivityInstances(
                    HistoricCaseActivityInstanceQueryDto queryDto) {
        var q = historyService.createHistoricCaseActivityInstanceQuery();
        if (queryDto != null) queryDto.toQuery(q);
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricCaseActivityInstanceResultDto::fromHistoricCaseActivityInstance)
                .toList();
    }
    public List<HistoricDecisionInstanceResultDto> queryHistoricDecisionInstances(
                    HistoricDecisionInstanceQueryDto queryDto) {
        var q = historyService.createHistoricDecisionInstanceQuery();
        if (queryDto != null) queryDto.toQuery(q);
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricDecisionInstanceResultDto::fromHistoricDecisionInstance)
                .toList();
    }
    public List<HistoricJobLogResultDto> queryHistoricJobLog(
                    HistoricJobLogQueryDto queryDto) {
        var q = historyService.createHistoricJobLogQuery();
        if (queryDto != null) queryDto.toQuery(q);
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricJobLogResultDto::fromHistoricJobLog)
                .toList();
    }
    public List<HistoricBatchResultDto> queryHistoricBatches(
                    HistoricBatchQueryDto queryDto) {
        var q = historyService.createHistoricBatchQuery();
        if (queryDto != null) queryDto.toQuery(q);
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricBatchResultDto::fromHistoricBatch)
                .toList();
    }
    public List<HistoricExternalTaskLogResultDto> queryHistoricExternalTaskLog(
                    HistoricExternalTaskLogQueryDto queryDto) {
        var q = historyService.createHistoricExternalTaskLogQuery();
        if (queryDto != null) queryDto.toQuery(q);
        return q.listPage(0, defaultMaxResults).stream()
                .map(HistoricExternalTaskLogResultDto::fromHistoricExternalTaskLog)
                .toList();
    }
}
