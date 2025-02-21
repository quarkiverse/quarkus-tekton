package io.quarkiverse.tekton.cli.pipeline;

import java.util.List;
import java.util.function.Function;

import io.quarkiverse.tekton.cli.common.Table;

public class PipelineTable extends Table<PipelineListItem> {

    private static final String API_VERSION = "API Version";
    private static final String NAME = "Name";
    private static final String IN_PROJECT = "In Project";
    private static final String INSTALLED = "Installed";

    private static final List<String> HEADERS = List.of(API_VERSION, NAME, IN_PROJECT, INSTALLED);
    private static final List<Function<PipelineListItem, String>> MAPPERS = List.of(PipelineListItem::getApiVersion,
            PipelineListItem::getName, PipelineListItem::isInProject, PipelineListItem::isInCluster);

    public PipelineTable(List<PipelineListItem> items) {
        super(HEADERS, items, MAPPERS);
    }
}
