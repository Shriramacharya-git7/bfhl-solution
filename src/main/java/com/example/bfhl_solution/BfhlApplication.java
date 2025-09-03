package com.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@SpringBootApplication
public class BfhlApplication {
    public static void main(String[] args) {
        SpringApplication.run(BfhlApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() { return new RestTemplate(); }

    @Bean
    public CommandLineRunner run(RestTemplate rt) {
        return args -> {
            // ----- CHANGE THESE 3 VALUES -----
            String name = "shriram";                // 👈 replace with your real name
            String regNo = "REG12347";                // 👈 replace with your reg number
            String email = "shriramacharya78@example.com";   // 👈 replace with your email
            // ----------------------------------

            String generateUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

            Map<String,String> request = Map.of("name", name, "regNo", regNo, "email", email);

            try {
                System.out.println("Calling generateWebhook...");
                ResponseEntity<Map> resp = rt.postForEntity(generateUrl, request, Map.class);
                Map body = resp.getBody();
                if (body == null) {
                    System.err.println("No body from generateWebhook");
                    return;
                }
                String webhook = (String) body.get("webhook");
                String accessToken = (String) body.get("accessToken");
                System.out.println("webhook = " + webhook);
                System.out.println("accessToken = " + accessToken);

                String digits = regNo.replaceAll("\\D+", "");
                int lastTwo = digits.length() == 0 ? 0 : Integer.parseInt(digits.substring(Math.max(0,digits.length()-2)));
                boolean isOdd = (lastTwo % 2) == 1;

                // FINAL QUERY strings
                String finalQ1 = "WITH valid_payments AS ( SELECT p.payment_id, p.emp_id, p.amount, p.payment_time FROM payments p WHERE EXTRACT(DAY FROM p.payment_time) <> 1 ), max_amt AS ( SELECT MAX(amount) AS mx FROM valid_payments ) SELECT vp.amount AS salary, e.first_name || ' ' || e.last_name AS name, EXTRACT(YEAR FROM AGE(DATE(vp.payment_time), e.dob))::int AS age, d.department_name FROM valid_payments vp JOIN max_amt m ON vp.amount = m.mx JOIN employee e ON e.emp_id = vp.emp_id JOIN department d ON d.department_id = e.department;";

                String finalQ2 = "SELECT e1.EMP_ID, e1.FIRST_NAME, e1.LAST_NAME, d.DEPARTMENT_NAME, COUNT(e2.EMP_ID) AS YOUNGER_EMPLOYEES_COUNT FROM EMPLOYEE e1 LEFT JOIN EMPLOYEE e2 ON e2.DEPARTMENT = e1.DEPARTMENT AND e2.DOB > e1.DOB JOIN DEPARTMENT d ON d.DEPARTMENT_ID = e1.DEPARTMENT GROUP BY e1.EMP_ID, e1.FIRST_NAME, e1.LAST_NAME, d.DEPARTMENT_NAME ORDER BY e1.EMP_ID DESC;";

                String finalQuery = isOdd ? finalQ1 : finalQ2;

                Path out = Paths.get("finalQuery.txt");
                Files.writeString(out, finalQuery);
                System.out.println("Saved finalQuery.txt");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", accessToken);
                Map<String,String> submitBody = Map.of("finalQuery", finalQuery);
                HttpEntity<Map<String,String>> entity = new HttpEntity<>(submitBody, headers);

                System.out.println("Posting finalQuery to webhook...");
                ResponseEntity<String> submitResp = rt.postForEntity(webhook, entity, String.class);
                System.out.println("Submit response: " + submitResp.getStatusCode() + " - " + submitResp.getBody());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        };
    }
}
