package models;

public class HeaderInfo {
    String id;
    String from;
    String subject;
    String date;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "HeaderInfo{" +
                "id='" + id + '\'' +
                ", from='" + from + '\'' +
                ", subject='" + subject + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
}
