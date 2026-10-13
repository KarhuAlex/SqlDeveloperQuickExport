package dev.quickexport.core;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** In-memory {@link ExportModel} for tests. */
final class ListModel implements ExportModel {

    private final String[] columns;
    private final List<Object[]> rows;

    ListModel(String[] columns, List<Object[]> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    static ListModel of(String[] columns, Object[]... rows) {
        return new ListModel(columns, Arrays.asList(rows));
    }

    static Object[] row(Object... values) {
        return values;
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public RowCursor openRows() {
        return new RowCursor() {
            int index = -1;

            @Override
            public boolean next() {
                return ++index < rows.size();
            }

            @Override
            public Object getValue(int column) {
                return rows.get(index)[column];
            }

            @Override
            public void close() throws IOException {
                // nothing to release
            }
        };
    }
}
