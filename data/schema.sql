--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

SET search_path = public, pg_catalog;

ALTER TABLE ONLY public.counters DROP CONSTRAINT repository_counter;
ALTER TABLE ONLY public.repositories DROP CONSTRAINT owner;
ALTER TABLE ONLY public.counters DROP CONSTRAINT counter_type;
DROP INDEX public.fki_owner;
DROP INDEX public.counters_increment;
DROP INDEX public.counters_date;
ALTER TABLE ONLY public.repositories DROP CONSTRAINT repository_path;
ALTER TABLE ONLY public.repositories DROP CONSTRAINT repos_id;
ALTER TABLE ONLY public.owners DROP CONSTRAINT owner_name;
ALTER TABLE ONLY public.owners DROP CONSTRAINT owner_id;
ALTER TABLE ONLY public.counters DROP CONSTRAINT daily_counters_for_repositories;
ALTER TABLE ONLY public.counter_types DROP CONSTRAINT counter_type_id;
ALTER TABLE public.repositories ALTER COLUMN id DROP DEFAULT;
ALTER TABLE public.owners ALTER COLUMN id DROP DEFAULT;
ALTER TABLE public.counter_types ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE public.repositories_id_seq;
DROP TABLE public.repositories;
DROP SEQUENCE public.owners_id_seq;
DROP TABLE public.owners;
DROP TABLE public.counters;
DROP SEQUENCE public.counter_type_id_seq;
DROP TABLE public.counter_types;
DROP EXTENSION plpgsql;
DROP SCHEMA public;
--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: counter_types; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE counter_types (
    id integer NOT NULL,
    name character varying(256)
);


--
-- Name: counter_type_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE counter_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: counter_type_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE counter_type_id_seq OWNED BY counter_types.id;


--
-- Name: counters; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE counters (
    date date NOT NULL,
    repository_id bigint NOT NULL,
    counter_id integer NOT NULL,
    increment integer,
    count integer
);


--
-- Name: owners; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE owners (
    id bigint NOT NULL,
    github_id bigint,
    name character varying(256) NOT NULL,
    avatar_url character varying(256),
    gravatar_id integer,
    type character varying(50),
    site_admin boolean
);


--
-- Name: owners_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE owners_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: owners_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE owners_id_seq OWNED BY owners.id;


--
-- Name: repositories; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE repositories (
    id bigint NOT NULL,
    github_id bigint,
    name character varying(256),
    full_name character varying(512),
    owner_id bigint,
    description text,
    created_at date,
    updated_at date,
    pushed_at date,
    size bigint,
    is_fork boolean,
    forks_ids bigint[],
    parent_id bigint,
    is_clojure boolean DEFAULT true
);


--
-- Name: COLUMN repositories.full_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN repositories.full_name IS 'path to repos';


--
-- Name: repositories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE repositories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: repositories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE repositories_id_seq OWNED BY repositories.id;


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY counter_types ALTER COLUMN id SET DEFAULT nextval('counter_type_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY owners ALTER COLUMN id SET DEFAULT nextval('owners_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY repositories ALTER COLUMN id SET DEFAULT nextval('repositories_id_seq'::regclass);


--
-- Name: counter_type_id; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY counter_types
    ADD CONSTRAINT counter_type_id PRIMARY KEY (id);


--
-- Name: daily_counters_for_repositories; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY counters
    ADD CONSTRAINT daily_counters_for_repositories PRIMARY KEY (date, repository_id, counter_id);


--
-- Name: owner_id; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY owners
    ADD CONSTRAINT owner_id PRIMARY KEY (id);


--
-- Name: owner_name; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY owners
    ADD CONSTRAINT owner_name UNIQUE (name);


--
-- Name: repos_id; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY repositories
    ADD CONSTRAINT repos_id PRIMARY KEY (id);


--
-- Name: repository_path; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY repositories
    ADD CONSTRAINT repository_path UNIQUE (full_name);


--
-- Name: counters_date; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX counters_date ON counters USING btree (date DESC);


--
-- Name: counters_increment; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX counters_increment ON counters USING btree (increment NULLS FIRST);


--
-- Name: fki_owner; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX fki_owner ON repositories USING btree (owner_id);


--
-- Name: counter_type; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY counters
    ADD CONSTRAINT counter_type FOREIGN KEY (counter_id) REFERENCES counter_types(id);


--
-- Name: owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY repositories
    ADD CONSTRAINT owner FOREIGN KEY (owner_id) REFERENCES owners(id);


--
-- Name: repository_counter; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY counters
    ADD CONSTRAINT repository_counter FOREIGN KEY (repository_id) REFERENCES repositories(id);


--
-- PostgreSQL database dump complete
--

