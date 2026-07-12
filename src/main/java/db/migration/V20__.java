package db.migration;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class V20__ implements org.flywaydb.core.api.migration.JavaMigration {

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("20");
    }

    @Override
    public Integer getChecksum() {
        return 20260713;
    }

    @Override
    public String getDescription() {
        return "Fix legacy question version numbers with SHA-1";
    }

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String dbProduct = connection.getMetaData().getDatabaseProductName().toLowerCase();
        boolean isMySQL = dbProduct.contains("mysql");
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<String> ids = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM questions WHERE version_number = '1'");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
        }

        if (!ids.isEmpty()) {
            try (PreparedStatement updateVersion = connection.prepareStatement(
                    "UPDATE questions SET version_number = ?, version_group_id = ?, version_status = 'ACTIVE' WHERE id = ?");

                 PreparedStatement insertChain = connection.prepareStatement(
                         "INSERT INTO question_version_chain (id, version_group_id, from_version_id, to_version_id, change_type, created_at) VALUES (?, ?, ?, ?, 'INITIAL', ?)")) {

                for (String id : ids) {
                    String versionNumber = generateSha1Short(id + "|" + System.nanoTime());
                    updateVersion.setString(1, versionNumber);
                    updateVersion.setString(2, id);
                    updateVersion.setString(3, id);
                    updateVersion.executeUpdate();

                    String chainId = UUID.randomUUID().toString();
                    insertChain.setString(1, chainId);
                    insertChain.setString(2, id);
                    insertChain.setString(3, id);
                    insertChain.setString(4, id);
                    insertChain.setString(5, now);
                    insertChain.executeUpdate();
                }
            }
        }

        try (Statement stmt = connection.createStatement()) {
            if (isMySQL) {
                try {
                    stmt.executeUpdate("CREATE INDEX idx_questions_version_number ON questions (version_number)");
                } catch (SQLException e) {
                    if (e.getErrorCode() != 1061) {
                        throw e;
                    }
                }
            } else {
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_questions_version_number ON questions (version_number)");
            }
        }
    }

    private String generateSha1Short(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.substring(0, 7);
    }
}
