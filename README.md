# Generation-Meter-To-Elastic

This project is for collecting data from an Elkor Watts On Meter and stashing it into an elastic search cluster for
further analytics and dashboards.  It supports limiting the total fields we place into elastic, as well as defining a
site for the device.

# Adding devices
There needs to be a servers.json file added which is located next to the current working path this is run on (`user.dir`).

An example of this file's content should look like: 
```{
{
  "servers": [
    {
        "name": "Server A1",
        "address": "http://xxx.xxx.xxx.xxx/setup/devicexml.cgi?ADDRESS=xxx&TYPE=DATA",
        "site": "Site A"
    }
    ]
}
