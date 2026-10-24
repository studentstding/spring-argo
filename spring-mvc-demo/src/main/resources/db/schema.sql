-- Składnia działa w MySQL i w H2 (profil dev uruchamia H2 w trybie MODE=MySQL)
CREATE TABLE IF NOT EXISTS note (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    text VARCHAR(500) NOT NULL
);
