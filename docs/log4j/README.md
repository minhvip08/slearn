# Log4j
Reading [Log4j](https://logging.apache.org/log4j/2.x/manual/architecture.html) to understand the architecture

## Log4j 2 Architecture Example

Below is a `log4j2.xml` configuration file built based on Log4j 2 architecture principles, along with a detailed explanation of each component's role in the system.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
  <!-- 1. Define Appenders (Log destinations) -->
  <Appenders>
    <Console name="LogToConsole" target="SYSTEM_OUT">
      <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
    </Console>

    <RollingFile name="LogToFile" fileName="logs/app.log" 
                 filePattern="logs/app-%d{MM-dd-yyyy}-%i.log.gz">
      <JsonTemplateLayout/>
      <Policies>
        <SizeBasedTriggeringPolicy size="10MB"/>
      </Policies>
    </RollingFile>
  </Appenders>

  <!-- 2. Define Loggers (Hierarchy and log levels configuration) -->
  <Loggers>
    <Logger name="com.mycompany.service" level="DEBUG" additivity="false">
      <AppenderRef ref="LogToFile"/>
    </Logger>

    <Root level="INFO">
      <AppenderRef ref="LogToConsole"/>
    </Root>
  </Loggers>
</Configuration>
```

### Detailed Explanation based on Log4j 2 Architecture

#### 1. `<Configuration>` Tag
* **Role**: This is the root node of the entire configuration system.
* `status="WARN"`: This defines the internal logging level for the Log4j library itself, helping to check if Log4j encounters any errors during initialization or configuration loading.

#### 2. `<Appenders>` Block
* **Definition**: Appenders are responsible for delivering `LogEvent`s to specific destinations such as the Console, a File, or a Socket.
* `<Console>`: Uses `ConsoleAppender` to write logs to the standard output (screen).
    * `PatternLayout`: Formats the log event into human-readable plain text (Timestamp, Thread, Level, Logger Name, Message).
* `<RollingFile>`: An advanced Appender that writes logs to a file and automatically rotates/compresses the file when it reaches a certain size or based on time.
    * `JsonTemplateLayout`: Formats the log into JSON format for centralized data analytics systems.
* `AbstractManager`: Underneath this Appender, a single `FileManager` manages writing data to the `app.log` file to ensure thread safety.

#### 3. `<Loggers>` Block
* **Definition**: Contains `LoggerConfig` objects that route the flow of log events.
* `<Logger name="com.mycompany.service" level="DEBUG">`:
    * Configures the logging behavior for classes in the `com.mycompany.service` package.
    * `level="DEBUG"`: Accepts all logs at the `DEBUG` level and above.
    * `additivity="false"`: Disables additivity. Logs from this package will only be sent to the `LogToFile` Appender and will not be propagated up to the `Root` logger (Console).
* `<Root level="INFO">`:
    * The highest level in the Logger hierarchy.
    * If a class does not have a specific Logger configuration, it inherits from the Root logger.
* `<AppenderRef ref="...">`:
    * The connecting reference between a `LoggerConfig` and an `Appender`.
    * It acts as the "final filter" before data is pushed to the executing Appender.

### Summary of the Execution Mechanism
1. **Source Code**: When you call `LOGGER.debug("...")`, a `LogEvent` is created.
2. **LoggerConfig**: Log4j finds the most specific `LoggerConfig` matching the class name (Longest Prefix Match).
3. **Filter/Level**: Checks if the `DEBUG` level is permitted to be logged.
4. **Forwarding**: If `additivity` is true, the event is sent to both its own Appender and its parent's Appender (Root).
5. **Execution**: The Appender uses the `Layout` to format the event and relies on a `Manager` to write it to the physical resource.