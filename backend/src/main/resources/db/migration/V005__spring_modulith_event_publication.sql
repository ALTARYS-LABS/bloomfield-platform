-- Table de stockage des evenements persistes par Spring Modulith.
-- Utilisee par JdbcEventPublicationRepositoryV2 (spring-modulith-events-jdbc 2.0.0)
-- pour garantir la livraison des evenements consommes par les listeners annotes
-- @ApplicationModuleListener (ex: AlertEngine.onQuoteTick).
--
-- DDL copie textuellement depuis la ressource officielle du jar:
--   org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql
-- On prefere une migration Flyway explicite plutot que l'auto-initialisation
-- Modulith afin de garder le schema 100% sous le controle de Flyway.

CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
  ON event_publication USING hash(serialized_event);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
  ON event_publication (completion_date);
