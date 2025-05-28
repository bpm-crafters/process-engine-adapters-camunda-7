# Process Engine Adapter Camunda 7


[![stable](https://img.shields.io/badge/lifecycle-STABLE-green.svg)](https://github.com/holisticon#open-source-lifecycle)
[![Development branches](https://github.com/bpm-crafters/process-engine-adapters-camunda-7/actions/workflows/development.yml/badge.svg)](https://github.com/bpm-crafters/process-engine-adapters-camunda-7/actions/workflows/development.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/dev.bpm-crafters.process-engine-adapters/process-engine-adapter-camunda-platform-c7-bom)](https://maven-badges.herokuapp.com/maven-central/dev.bpm-crafters.process-engine-adapters/process-engine-adapter-camunda-platform-c7-bom)
[![Camunda Platform 7](https://img.shields.io/badge/Compatible%20with-Camunda%20Platform%207-26d07c)](https://img.shields.io/badge/Compatible%20with-Camunda%20Platform%207-26d07c)


## Purpose of the library

This library provides an adapter implementation of Process Engine API for Camunda 7 process engine. 

## Anatomy

The library contains of the following Maven modules:

- `process-engine-adapter-camunda-platform-c7-embedded-core`: Camunda 7 Platform Embedded Adapter implementation 
- `process-engine-adapter-camunda-platform-c7-embedded-spring-boot-starter`: Camunda 7 Platform Embedded Adapter Spring Boot Starter 
- `process-engine-adapter-camunda-platform-c7-remote-core`: Camunda 7 Platform Remote Adapter implementation 
- `process-engine-adapter-camunda-platform-c7-remote-spring-boot-starter`: Camunda 7 Platform Remote Adapter Spring Boot Starter 
- `process-engine-adapter-camunda-platform-c7-bom`: Maven BOM containing dependency definitions.

## Usage

If you want to start usage, please add the BOM to your Maven project and add corresponding adapter implementation:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
  <artifactId>process-engine-adapter-camunda-platform-c7-bom</artifactId>
  <version>${process-engine-adapter-camunda-7.version}</version>
  <scope>import</scope>
  <type>pom</type>
</dependency>
```

## Compatibility

| Adapter-7 Version | Camunda 7 Version | API Version |
|-------------------|-------------------|-------------|
| 2025.05.5         | 7.23              | 1.2         |
| 2025.05.4         | 7.23              | 1.2         |
| 2025.05.3         | 7.23              | 1.2         |
| 2025.05.2         | 7.23              | 1.1         |
| 2025.05.1         | 7.23              | 1.1         |
| 2025.04.4         | 7.22              | 1.1         |
| 2025.04.3         | 7.22              | 1.1         |
| 2025.04.2         | 7.22              | 1.0         |
| 2025.04.1         | 7.22              | 1.0         |



