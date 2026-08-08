package com.weinhold.hexagon.contact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcUrlTests {

    @Test
    void parsesHostBasedUrls() {
        JdbcUrl postgres = JdbcUrl.parse("jdbc:postgresql://db:5432/orders?ssl=true");
        assertThat(postgres.vendor()).isEqualTo("postgresql");
        assertThat(postgres.database()).isEqualTo("orders");

        JdbcUrl mysql = JdbcUrl.parse("jdbc:mysql://localhost/shop");
        assertThat(mysql.vendor()).isEqualTo("mysql");
        assertThat(mysql.database()).isEqualTo("shop");
    }

    @Test
    void parsesEmbeddedAndSpecialUrls() {
        JdbcUrl h2 = JdbcUrl.parse("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        assertThat(h2.vendor()).isEqualTo("h2");
        assertThat(h2.database()).isEqualTo("testdb");

        JdbcUrl sqlite = JdbcUrl.parse("jdbc:sqlite:/var/data/orders.db");
        assertThat(sqlite.vendor()).isEqualTo("sqlite");
        assertThat(sqlite.database()).isEqualTo("orders");
    }

    @Test
    void returnsNullForNonJdbc() {
        assertThat(JdbcUrl.parse("postgres://db/orders")).isNull();
        assertThat(JdbcUrl.parse(null)).isNull();
    }

}
