package models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Developer extends User {
    private ExpertiseArea expertiseArea;
    private Seniority seniority;
    private String hireDate;

    private double performanceScore = 0.0;

    public Developer() {
        super();
        // Setăm rolul explicit, deși Jackson îl va seta automat din JSON
        this.setRole(Role.DEVELOPER);
    }
}