import java.sql.*;

public class audit_trace {
    public static void main(String[] args) throws Exception {
        String pw = System.getenv("DBPW");
        Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5434/ai_study_hub", "postgres", pw);
        Statement s = c.createStatement();

        System.out.println("=== SUBJECTS (first 5) ===");
        ResultSet r = s.executeQuery("SELECT id, semester_id, code FROM subject ORDER BY code LIMIT 5");
        while (r.next()) System.out.println("  id=" + r.getString(1) + " semester=" + r.getString(2) + " code=" + r.getString(3));

        System.out.println("=== FOLDERS with subject (first 5) ===");
        r = s.executeQuery("SELECT id, name, subject_id FROM folder WHERE deleted_at IS NULL LIMIT 5");
        while (r.next()) System.out.println("  id=" + r.getString(1) + " name=" + r.getString(2) + " subject_id=" + r.getString(3));

        System.out.println("=== DOCUMENTS with subject (first 5) ===");
        r = s.executeQuery("SELECT id, title, subject_id, folder_id FROM document WHERE deleted_at IS NULL LIMIT 5");
        while (r.next()) System.out.println("  id=" + r.getString(1) + " title=" + r.getString(2) + " subject_id=" + r.getString(3) + " folder_id=" + r.getString(4));

        System.out.println("=== Mismatch: doc.subject_id NOT in subject ===");
        r = s.executeQuery("SELECT d.id, d.subject_id FROM document d LEFT JOIN subject sub ON sub.id = d.subject_id WHERE d.deleted_at IS NULL AND sub.id IS NULL LIMIT 5");
        int n = 0;
        while (r.next()) { System.out.println("  doc=" + r.getString(1) + " subject_id=" + r.getString(2)); n++; }
        if (n == 0) System.out.println("  (none)");

        System.out.println("=== Mismatch: folder.subject_id NOT in subject ===");
        r = s.executeQuery("SELECT f.id, f.subject_id FROM folder f LEFT JOIN subject sub ON sub.id = f.subject_id WHERE f.deleted_at IS NULL AND sub.id IS NULL LIMIT 5");
        n = 0;
        while (r.next()) { System.out.println("  folder=" + r.getString(1) + " subject_id=" + r.getString(2)); n++; }
        if (n == 0) System.out.println("  (none)");

        System.out.println("=== verify token for zx_trace_1 ===");
        r = s.executeQuery("SELECT t.token, t.expires_at FROM account_token t JOIN account a ON a.id = t.account_id WHERE a.username='zx_trace_1' AND t.type='EMAIL_VERIFICATION' ORDER BY t.created_at DESC LIMIT 1");
        while (r.next()) System.out.println("  token=" + r.getString(1) + " expires=" + r.getTimestamp(2));
        c.close();
    }
}
