[![CodeQL](https://github.com/bigboxer23/Generation-Meter-To-Elastic/actions/workflows/codeQL.yml/badge.svg)](https://github.com/bigboxer23/Generation-Meter-To-Elastic/actions/workflows/codeQL.yml)

# Generation-Meter-To-Elastic

This project is for collecting data from an Elkor Watts On Meter and stashing it into an elastic search cluster for
further analytics and dashboards.  It supports limiting the total fields we place into elastic, as well as defining a
site for the device.

# Adding devices

There needs to be a servers.json file added which is located next to the current working path this is run on (`user.dir`).
This file should contain a list of servers which define specific devices to gather information from.  There is also the
concept of a "site", which is a virtual device that will aggregate all the single devices which have a matching site
attribute into a single "site" device.

An example of this file's content should look like:

```{
{
  "servers": [
    {
        "name": "Server A1",
        "address": "http://xxx.xxx.xxx.xxx/setup/devicexml.cgi?ADDRESS=xxx&TYPE=DATA",
        "site": "Site A"
    }
    ],
    "sites": [
    {
      "name" : "Site A"
    }
  ]
}
```

