# verotel

Micronaut + Kotlin aplikace: Micronaut Data JDBC, PostgreSQL, Liquibase, Thymeleaf.

## Spuštění lokálně

```bash
cp .env.example .env          # jednorázově, pak doplnit hodnoty
./run.sh
```

Skript spustí Postgres přes Docker Compose, načte proměnné z `.env` a nastartuje
aplikaci na http://localhost:8080. Soubor `.env` je mimo git.

Připojení do databáze se čte z proměnných prostředí `POSTGRES_URL`, `POSTGRES_USER`,
`POSTGRES_PASSWORD` (a volitelně `PORT`, výchozí 8080).

## Testy

```bash
mvn test
```

Testy si přes Testcontainers spouští vlastní PostgreSQL v Dockeru — Docker musí běžet.
