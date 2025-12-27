package models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Developer extends User {
    private String hireDate;
    private Seniority seniority;
    private ExpertiseArea expertiseArea;
}
