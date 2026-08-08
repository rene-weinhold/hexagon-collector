package com.weinhold.hexagon.contact;

/**
 * Best-effort parse of a JDBC URL into a {@code vendor} and {@code database}, enough to
 * build a {@code jdbc:{vendor}/{database}} canonical key. JDBC URLs are wildly
 * vendor-specific, so this recognizes the common shapes and leaves {@code database} null
 * when it cannot tell.
 */
public record JdbcUrl(String vendor, String database) {

    public static JdbcUrl parse(String url) {
        if (url == null || !url.startsWith("jdbc:")) {
            return null;
        }
        var rest = url.substring("jdbc:".length());

        var vendorEnd = indexOfAny(rest, ':', '/');
        if (vendorEnd <= 0) {
            return new JdbcUrl(rest.isBlank() ? null : rest, null);
        }
        var vendor = rest.substring(0, vendorEnd);
        return new JdbcUrl(vendor, database(rest.substring(vendorEnd)));
    }

    private static String database(String afterVendor) {
        // Strip any query string / connection properties first.
        var value = afterVendor;
        var query = indexOfAny(value, '?', ';');
        if (query >= 0) {
            value = value.substring(0, query);
        }

        var hostMarker = value.indexOf("//");
        if (hostMarker >= 0) {
            // e.g. ://host:5432/orders -> the segment after the host's first slash
            var pathStart = value.indexOf('/', hostMarker + 2);
            var database = pathStart >= 0 ? value.substring(pathStart + 1) : "";
            return blankToNull(trimSlashes(database));
        }

        // e.g. h2:mem:testdb, sqlite:/path/to/orders.db, oracle:thin:@host:1521:ORCL
        var lastSeparator = Math.max(value.lastIndexOf('/'), value.lastIndexOf(':'));
        var database = lastSeparator >= 0 ? value.substring(lastSeparator + 1) : value;
        return blankToNull(stripFileExtension(trimSlashes(database)));
    }

    private static int indexOfAny(String value, char a, char b) {
        var first = value.indexOf(a);
        var second = value.indexOf(b);
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private static String trimSlashes(String value) {
        var start = 0;
        var end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
    }

    private static String stripFileExtension(String value) {
        var dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

}
