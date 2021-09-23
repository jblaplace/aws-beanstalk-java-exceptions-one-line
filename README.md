# Overview
Often, applications deployed to AWS Elastic Beanstalk will want to stream their logs to AWS Cloud Watch Logs. 
Application and exception logs are essential to troubleshoot and diagnose issues.

In the world of Java applications logging stack trace is fundamental in understanding what has gone wrong during the execution of the application.

AWS Elastic Beanstalk will log to CloudWatch Logs your Java web application logs when you activate: "Instance log streaming to CloudWatch Logs".
Once activated, application logs from the BeanStalk environment are sent to the following log group: 
> /aws/elasticbeanstalk/`your environment`/log/web.stdout.log

This log group will contain a log stream for each instance running in that environment.
Each line of the stack trace is written as one entry in CloudWatch log, potentially causing you problems when searching for logs and exceptions in CloudWatch.
For example, when searching in CloudWatch for the key word *Exception* your get results where the key word is found but you lose the context (stack trace) of the exception.

This document and sample application provides the detail steps required to keep the stack trace as one entry in CloudWatch Logs.

# Prerequisites and limitations
- The solution applies to a Java Web Server AWS Elastic Beanstalk environment running Amazon Linux instances
- The Java Application leverage a common logging framework. The solution provides examples using Logback

# High Level Design
The solution leverage standard tools:
- The Java Application uses a logger such as Logback to log to a local file
    - The log file is monitored and streamed back to CloudWatch through the CloudWatch Logs agent
    - The agent is pre-installed on the instance(s) in the environment

# Solution Details
Before we dive into the details of the solution lets understand why stack trace are logged the way they are in CloudWatch logs.

AWS Elastic Beanstalk executes your Java Web Application using a command similar to the following: 
>java -jar target/*your app*.jar

The output of the java command is sent to standard out (stdout) and standard error (stderr)
The AWS Elastic Beanstalk environment leverage Linux rsyslog to capture stdout and stderr and write the logs into log files.

This is done through standard rsyslog configuration found here:

>    /etc/rsyslog.d/web.conf

    if $programname  == 'web' then {
        *.=warning;*.=err;*.=crit;*.=alert;*.=emerg /var/log/web.stderr.log
        *.=info;*.=notice /var/log/web.stdout.log   
    }   

It is rsyslog that interprets the stack trace from stdout as multiple entries and writes multiple lines in:
> /var/log/web.stdout.log. 

The CloudWatch Logs agent simply streams the content of /var/log/web.stdout.log back to Cloud Watch Logs log group:
> /aws/elasticbeanstalk/`your environment`/log/web.stdout.log without any transformation.

To remediate to this problem the following approach is recommended:
- First the application should write its log to its own log file managed by the application Logger

Here we use Logback and define a FILE appender that writes our application logs to /opt/myapp/myapp.log

It is highly recommended to keep the log file outside of /var/app/current which is the directory where the application is deployed. 
The reason for that is that directory gets recreated on subsequent deploments.

    <?xml version="1.0" encoding="UTF-8"?>
    <configuration>
        <!-- Send debug messages to System.out -->
        <appender name="STDOUT"
            class="ch.qos.logback.core.ConsoleAppender">
            <!-- By default, encoders are assigned the type ch.qos.logback.classic.encoder.PatternLayoutEncoder -->
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{5} - %msg %xException %nopex %n
                </pattern>
                <!-- <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{5} - %m %replace(%ex){'[\r\n]+',
                    ''}%nopex %n</pattern> -->
            </encoder>
        </appender>

        <appender name="FILE" class="ch.qos.logback.core.FileAppender">
            <file>/opt/myapp/myapp.log</file>
            <append>true</append>
            <!-- set immediateFlush to false for much higher logging throughput -->
            <immediateFlush>true</immediateFlush>
            <!-- encoders are assigned the type ch.qos.logback.classic.encoder.PatternLayoutEncoder
                by default -->
            <encoder>
                <pattern>%-4relative [%thread] %-5level %logger{35} - %msg %xException %nopex %n</pattern>
            </encoder>
        </appender>


        <logger name="info.laplace.awsjb.springbootapp" level="DEBUG"
            additivity="false">
            <appender-ref ref="STDOUT" />
            <appender-ref ref="FILE" />
        </logger>

        <!-- By default, the level of the root level is set to DEBUG -->
        <root level="INFO">
            <appender-ref ref="STDOUT" />
        </root>
    </configuration>

- Pay attention to the pattern: \<pattern>%-4relative [%thread] %-5level %logger{35} - %msg **%xException %nopex**%n\</pattern>
- You now need to configure your Beanstalk Environment to stream the application log file back to CloudWatch Logs
    - Leverage **.ebextensions** and create a **.config** file which simply creates a temporary file on the Beanstalk instance. 
    - That file is a CloudWatch Logs agent configuration file.
    - The file also creates the directory that will hold the log file and sets the proper permissions on the directory. 
    - The file should reside in the .ebextensions folder found at the root of the Application  Zip/package sent to Beanstalk. 
        > Ex: /.ebexternsions/myapp-log.config

>
        files:
        "/tmp/myapp-log.json" :
            mode: "000644"
            owner: root
            group: root
            content: |
                {
                    "logs": {
                        "logs_collected": {
                            "files": {
                                "collect_list": [
                                    {
                                        "file_path": "/opt/myapp/myapp.log",
                                        "log_group_name" : "`{"Fn::Join":["/", ["/aws/elasticbeanstalk", { "Ref":"AWSEBEnvironmentName" }, "opt/myapp/myapp.log"]]}`",
                                        "log_stream_name": "{instance_id}"
                                    }
                                ]
                            }
                        }
                    }
        }
        commands:
            01directories:
                command: "mkdir -p /opt/myapp/logs"
            02directories:
                command: "chown -R webapp:webapp /opt/myapp" 
        
- Leverage **.platform** configuration hooks to copy the temporary file /tmp/myapp-log.json to the proper CloudWatch agent configuration directory. 
    - We use the **confighooks** and **hooks** **postdeploy** hooks to execute a scrip that copies over the configuration 
    > Ex: /.platform/confighooks/postdeploy/setupCWL.sh and 
    > /.platform/hooks/postdeploy/setupCWL.sh
    
    - The content of the script is the following:
>
        #!/bin/sh
        echo "About to: cp /tmp/myapp-log.json /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.d"
        cp /tmp/myapp-log.json /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.d
        echo "About to: systemctl restart amazon-cloudwatch-agent"
        /bin/systemctl restart amazon-cloudwatch-agent
- It is important to note that the script file must be executable on the AWS Elastic Beanstalk instance to run properly

- Your final application package Layout should be as follow:
>
    <your package>.zip
        .ebextensions
            myapp-log.config
        .platform
            confighooks
                postedeploy
                    setupCWL.sh
            hooks
                postedeploy
                    setupCWL.sh
        your-app.jar

The Log in CloudWatch Logs will be one entry as desired. There will be a new Log group /aws/elasticbeanstalk/`your environment`/opt/myapp/myapp.log with a stream per instance.

# FAQ
- Q. How do you package your Java Application?
    - A. I use Maven. I leverage the maven-assembly-plugin build plugin and its associated zip.xml file to properly package the zip file containing the application Jar file, .ebextensions and .platform . That zip file is the application package for AWS Beanstalk.

# Resources

- AWS Beanstalk Extensions: https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/ebextensions.html
- AWS Beanstalk Platform hooks: https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/platforms-linux-extend.html
- LogBack Layout and Patterns: http://logback.qos.ch/manual/layouts.html
