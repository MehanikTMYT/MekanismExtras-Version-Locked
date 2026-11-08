# Mekanism Extras — NL source provenance

- Дата начала: 2026-10-27.
- Community source: https://github.com/victor0hxz/MekanismExtras-Version-Locked
- Точная база: `7e07b3d165b0c50eee82ab0f286edff74422beb5` (`main`).
- Upstream: https://github.com/lostmyself8/Mekanism-Extras, MIT (LICENSE сохранён).
- Рабочая ветка: `port/26.1-nl`; отдельный новый клон `/root/home/forks/mekanism-stack/Mekanism-Extras`.
- Сразу после clone Git показывал только нормализацию CRLF→LF в `src/main/resources/mekextra.mixins.json`, заданную `.gitattributes`; сторонних правок в новом клоне не было.

## Локальная платформа

- Java 25 / Gradle 9.6.1 / ModDevGradle 2.0.146 / NeoForge 26.1.2.122.
- Mekanism core + Generators: `26.1.2-10.8.0.homebaked` из mavenLocal; Additions/Tools — compileOnly.
- MoreMachine: существующий экспорт `mekmm-26.1.2-2.0.0.jar`, SHA-256 `2d6b7bcd3b341cafc7603bfca021bc0cdd1b55d9b8204b6ebce268b9b8d5fa35`, commit `48e51b90`. BUILD/SERVER PASS подтверждены отчётом NL.
- SEMI/EMI: `1.1.24-semi151a6c88+26.1.2` из mavenLocal.
- Исходники JEI-интеграции пока сохранены с compileOnly API 26.1.2; JEI не добавлен в runtime.
- Evolved Mekanism 1.21.1 сохранён исключительно как compileOnly для аудита существующей optional-интеграции. Это не подтверждение её совместимости с 26.1.2. IgleeLibrary и Evolved в runtime отсутствуют.

Убраны зависимости от локальных Windows H:/ путей и Version-Locked binary modules. Никаких заглушек API не добавлено, исходники машин/реактора не исключены из компиляции. Никакие изменения в Mekanism, MoreMachine, AE2 не внесены. Push/merge не выполнялись.
