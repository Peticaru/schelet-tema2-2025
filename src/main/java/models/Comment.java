package models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reprezintă un comentariu adăugat de un utilizator la un tichet.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
    private String author;
    private String content;

    private String createdAt;
}