package io.quarkiverse.tekton.cli.common;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

public class Table<T extends ListItem> {

    private static final String NEWLINE = "\n";

    private List<String> headers;
    private List<T> items;
    private List<Function<T, String>> mappers;

    private String indent = "";
    private boolean showHeader = true;

    public Table(List<String> headers, List<T> items, List<Function<T, String>> mappers) {
        this.headers = headers;
        this.mappers = mappers;
        this.items = items;

        if (headers.size() != mappers.size()) {
            throw new IllegalArgumentException("Headers and mappers must have the same length");
        }
    }

    public String getContent() {
        String format = getFormat();
        StringBuilder sb = new StringBuilder();
        if (showHeader) {
            sb.append(indent);
            sb.append(getHeader(format));
            sb.append(NEWLINE);
        }
        sb.append(getBody(format, items, indent));
        return sb.toString();
    }

    private String getHeader(String format) {
        return String.format(format, headers.toArray(String[]::new));
    }

    private String getBody(String format, Collection<T> items, String indent) {
        StringBuilder sb = new StringBuilder();
        for (T item : items) {
            sb.append(indent);
            sb.append(String.format(format, item.getFields()));
            sb.append(NEWLINE);
        }
        return sb.toString();
    }

    public void print() {
        System.out.println(getContent());
    }

    private String getFieldFormat(String header, List<T> items, Function<T, String> mapper) {
        int maxLength = Stream.concat(Stream.of(header),
                items.stream().map(mapper))
                .filter(Objects::nonNull)
                .map(String::length)
                .max(Comparator.naturalOrder())
                .orElse(0);
        return " %-" + maxLength + "s \t";
    }

    private String getFormat() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            sb.append(getFieldFormat(headers.get(i), items, mappers.get(i)));
        }
        return sb.toString();
    }
}
