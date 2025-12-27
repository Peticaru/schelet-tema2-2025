package main;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import commands.CommandInput;
import models.User;
import services.CommandRunner;
import services.TicketSystem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * App reprezintă logica principală care procesează input-ul,
 * generează output-ul și scrie rezultatul în fișier.
 */
public class App {

    // Constructor privat pentru a ascunde constructorul public implicit (Utility Class)
    private App() {
    }

    private static final String INPUT_USERS_PATH = "input/database/users.json";

    // Configurare Writer pentru a scrie JSON-ul final frumos formatat
    private static final ObjectWriter WRITER =
            new ObjectMapper().writer().withDefaultPrettyPrinter();

    /**
     * Rulează aplicația: citește comenzi, le procesează și scrie rezultatul.
     *
     * @param inputPath  calea către fișierul de intrare cu comenzi (ex: in_01_test.json)
     * @param outputPath calea către fișierul de ieșire
     */
    public static void run(final String inputPath, final String outputPath) {
        // Aceasta este lista în care vom adăuga rezultatele JSON ale comenzilor
        List<ObjectNode> outputs = new ArrayList<>();

        try {
            // ---------------------------------------------------------
            // 1. Configurare Jackson (Parser JSON)
            // ---------------------------------------------------------
            ObjectMapper mapper = new ObjectMapper();

            // Avem nevoie de JavaTimeModule pentru a parsa corect LocalDate ("yyyy-MM-dd")
            mapper.registerModule(new JavaTimeModule());
            // Dezactivăm scrierea datelor ca timestamp numeric (vrem string-uri)
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            // ---------------------------------------------------------
            // 2. Inițializare și Resetare Sistem (Singleton)
            // ---------------------------------------------------------
            // FOARTE IMPORTANT: Resetăm singleton-ul înainte de fiecare test
            // pentru a nu păstra date din testul anterior (checker-ul rulează secvențial).
            TicketSystem ticketSystem = TicketSystem.getInstance();
            ticketSystem.reset();

            // ---------------------------------------------------------
            // 3. Încărcare Utilizatori (Database)
            // ---------------------------------------------------------
            File usersFile = new File(INPUT_USERS_PATH);
            if (usersFile.exists()) {
                // Citim lista de useri. Jackson va ști să creeze Developer/Manager/Reporter
                // pe baza adnotărilor din clasa User (pe care o vom face imediat).
                List<User> users = mapper.readValue(
                        usersFile,
                        new TypeReference<List<User>>() {}
                );
                ticketSystem.loadUsers(users);
            }

            // ---------------------------------------------------------
            // 4. Citire Comenzi
            // ---------------------------------------------------------
            File commandsFile = new File(inputPath);
            List<CommandInput> commands = mapper.readValue(
                    commandsFile,
                    new TypeReference<List<CommandInput>>() {}
            );

            // ---------------------------------------------------------
            // 5. Procesare Comenzi (Design Pattern: Command / Delegate)
            // ---------------------------------------------------------
            CommandRunner commandRunner = new CommandRunner();

            for (CommandInput command : commands) {
                // Delegăm execuția. CommandRunner va popula lista 'outputs'.
                commandRunner.execute(command, outputs);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            File outputFile = new File(outputPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            WRITER.writeValue(outputFile, outputs);
        } catch (IOException e) {
            System.out.println("Eroare la scrierea fișierului de output: " + e.getMessage());
        }
    }
}