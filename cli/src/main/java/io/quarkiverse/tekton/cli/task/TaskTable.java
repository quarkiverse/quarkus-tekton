package io.quarkiverse.tekton.cli.task;

import java.util.List;
import java.util.function.Function;

import io.quarkiverse.tekton.cli.common.Table;

public class TaskTable extends Table<TaskListItem> {

    private static final String API_VERSION = "API Version";
    private static final String NAME = "Name";
    private static final String IN_PROJECT = "In Project";
    private static final String INSTALLED = "Installed";

    private static final List<String> HEADERS = List.of(API_VERSION, NAME, IN_PROJECT, INSTALLED);
    private static final List<Function<TaskListItem, String>> MAPPERS = List.of(TaskListItem::getApiVersion,
            TaskListItem::getName, TaskListItem::isInProject, TaskListItem::isInCluster);

    public TaskTable(List<TaskListItem> items) {
        super(HEADERS, items, MAPPERS);
    }
}
