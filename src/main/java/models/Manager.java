package models;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class Manager extends User {
    private String hireDate;
    private List<String> subordinates;
}
