#!/bin/sh
echo "About to: cp /tmp/myapp-log.json /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.d"
cp /tmp/myapp-log.json /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.d
echo "About to: systemctl restart amazon-cloudwatch-agent"
/bin/systemctl restart amazon-cloudwatch-agent