package coupons.api;

import coupons.repository.PostgresConnectionFactory;
import java.io.IOException;
import java.sql.*;
import java.util.Objects;

/** Durable retry results. An interrupted write is never blindly executed again. */
final class PostgresApiRequestStore implements ApiRequestStore {
    private final PostgresConnectionFactory connections;
    PostgresApiRequestStore(PostgresConnectionFactory connections) { this.connections=Objects.requireNonNull(connections); }
    @Override public Result execute(String scope,String key,String signature,Write write) throws IOException {
        // A session lock coordinates API instances without changing existing repository transactions.
        // The durable pending marker makes the unavoidable cross-transaction crash window explicit.
        String lock = scope + ":" + key;
        try (Connection c=connections.open()) {
            try (PreparedStatement p=c.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?, 0))")) {
                p.setString(1,lock); p.execute();
            }
            try {
                try (PreparedStatement p=c.prepareStatement("SELECT signature, status_code, response_body FROM api_write_requests WHERE scope=? AND request_key=?")) {
                    p.setString(1,scope);p.setString(2,key);
                    try(ResultSet r=p.executeQuery()) {
                        if(r.next()) {
                            if(!signature.equals(r.getString(1))) throw new Conflict("Idempotency key was used for a different request.");
                            int status=r.getInt(2);
                            if(r.wasNull()) throw new Conflict("Previous request outcome is uncertain. Retrieve the resource and reconcile before sending a new write.");
                            return new Result(status,r.getString(3));
                        }
                    }
                }
                try (PreparedStatement p=c.prepareStatement("INSERT INTO api_write_requests(scope,request_key,signature) VALUES (?,?,?)")) {
                    p.setString(1,scope);p.setString(2,key);p.setString(3,signature);p.executeUpdate();
                }
                Result result=write.run();
                try (PreparedStatement p=c.prepareStatement("UPDATE api_write_requests SET status_code=?,response_body=? WHERE scope=? AND request_key=?")) {
                    p.setInt(1,result.status());p.setString(2,result.body());p.setString(3,scope);p.setString(4,key);p.executeUpdate();
                }
                return result;
            } finally {
                try (PreparedStatement p=c.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))")) { p.setString(1,lock);p.execute(); }
            }
        } catch (SQLException e) { throw new IllegalStateException("API retry journal is unavailable.",e); }
    }
}
