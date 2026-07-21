package br.com.demo.ocistreaming.consumer;

import br.com.demo.ocistreaming.domain.OrderEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProcessedEventRepository implements ProcessedEventRepository {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_PROCESSED = "PROCESSED";
    private static final String STATUS_FAILED = "FAILED";

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean claimForProcessing(OrderEvent event) {
        List<String> statuses = jdbcTemplate.query(
                "select status from processed_events where event_id = ?",
                new RowMapper<String>() {
                    @Override
                    public String mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return rs.getString("status");
                    }
                },
                event.getEventId());

        if (statuses.isEmpty()) {
            try {
                jdbcTemplate.update(
                        "insert into processed_events " +
                                "(event_id, order_id, status, first_seen_at, updated_at) " +
                                "values (?, ?, ?, current_timestamp, current_timestamp)",
                        event.getEventId(),
                        event.getOrderId(),
                        STATUS_PROCESSING);
                return true;
            } catch (DataIntegrityViolationException duplicate) {
                return claimForProcessing(event);
            }
        }

        String status = statuses.get(0);
        if (STATUS_PROCESSED.equals(status)) {
            return false;
        }

        jdbcTemplate.update(
                "update processed_events " +
                        "set status = ?, last_error = null, updated_at = current_timestamp " +
                        "where event_id = ? and status in (?, ?)",
                STATUS_PROCESSING,
                event.getEventId(),
                STATUS_FAILED,
                STATUS_PROCESSING);
        return true;
    }

    @Override
    public void markProcessed(OrderEvent event) {
        jdbcTemplate.update(
                "update processed_events " +
                        "set status = ?, processed_at = current_timestamp, updated_at = current_timestamp, last_error = null " +
                        "where event_id = ?",
                STATUS_PROCESSED,
                event.getEventId());
    }

    @Override
    public void markFailed(OrderEvent event, Exception exception) {
        jdbcTemplate.update(
                "update processed_events " +
                        "set status = ?, updated_at = current_timestamp, last_error = ? " +
                        "where event_id = ?",
                STATUS_FAILED,
                limit(exception.getMessage()),
                event.getEventId());
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
