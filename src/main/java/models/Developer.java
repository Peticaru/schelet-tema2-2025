package models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Developer extends User {
    private String hireDate;
    private Seniority seniority;
    private ExpertiseArea expertiseArea;
    private double lastPerformanceScore = 0.0; // Se actualizează la fiecare raport
}
