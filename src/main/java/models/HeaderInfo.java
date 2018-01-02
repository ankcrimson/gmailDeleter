package models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class HeaderInfo {
    String id;
    String from;
    String subject;
    String date;
}
