JobSchedulerCompat
==================
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/me.tatarka.support/jobscheduler/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/me.tatarka.support/jobscheduler)

A backport of Android Lollipop's JobScheduler to api 10+.

All JobScheduler features are implemented. However, this library has not been
well-tested, so I would advise not using in production at this time. There are
no guarantees that this will not run down your battery or cause your device to
explode.

## Help needed
After building this, I've discovered that I really don't have the interest in maintaining it. It still requires significant work to get into a production-quality state. And above all, it needs significant work monitoring battery usage and finding the optimal strategies between battery usage and running jobs in a timely manner. If anyone is interested, I'll gladly answer any question about implementation details and what needs to be fixed. I won't however, spend time tracking down bugs.

### Gradle 
```groovy
compile 'me.tatarka.support:jobscheduler:0.1.1'
```
### Maven
```xml
<dependency>
  <groupId>me.tatarka.support</groupId>
  <artifactId>jobscheduler</artifactId>
  <version>0.1.1</version>
  <type>aar</type>
</dependency>
```

You also have to enable manifest merging to ensure the services and receivers are added to your manifest.
```xml
<plugin>
  <groupId>com.jayway.maven.plugins.android.generation2</groupId>
  <artifactId>android-maven-plugin</artifactId>
  <configuration>
    <mergeManifests>true</mergeManifests>
  </configuration>
</plugin>
```

## Usage

The api is identical to the official
[JobScheduler](http://developer.android.com/reference/android/app/job/JobScheduler.html),
the only differences will be what you import.

First create a service to run your job.

```java
import me.tatarka.support.job.JobParameters;
import me.tatarka.support.job.JobService;

/**
 * Service to handle callbacks from the JobScheduler. Requests scheduled with the JobScheduler
 * ultimately land on this service's "onStartJob" method.
 */
public class TestJobService extends JobService {
  @Override
  public boolean onStartJob(JobParameters params) {
    // Start your job in a seperate thread, calling jobFinished(params, needsRescheudle) when you are done.
    // See the javadoc for more detail.
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    // Stop the running job, returing true if it needs to be recheduled.
    // See the javadoc for more detail.
    return true;
  }
```

Then use the `JobScheduler` to schedule the job.

```java
import me.tatarka.support.job.JobInfo;
import me.tatarka.support.job.JobScheduler;
import me.tatarka.support.os.PersistableBundle;

// Get an instance of the JobScheduler, this will delegate to the system JobScheduler on api 21+ 
// and to a custom implementataion on older api levels.
JobScheduler jobScheduler = JobScheduler.getInstance(context);

// Extras for your job.
PersistableBundle extras = new PersistableBundle();
extras.putString("key", "value");

// Construct a new job with your service and some constraints.
// See the javadoc for more detail.
JobInfo job = new JobInfo.Builder(0 /*jobid*/, new ComponentName(context, TestJobService.class))
  .setMinimumLatency(1000)
  .setOverrideDeadline(2000)
  .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
  .setRequiresCharging(true)
  .setExtras(extras)
  .build();

jobScheudler.scheduleJob(job);
```

Finally, register your `JobService` in the Manifest. Since on api < 21 the
`JobScheduler` runs within your app, you may need additional permissions
depending on what you are doing.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
  package="me.tatarka.support.job.sample">

  <!-- Always required on api < 21, needed to keep a wake lock while your job is running -->
  <uses-permission android:name="android.permission.WAKE_LOCK" />
  <!-- Required on api < 21 if you are using setRequiredNetworkType(int) -->
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  <!-- Required on all api levels if you are using setPersisted(true) -->
  <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

  <application>
    ...
    
    <!-- Define your service, make sure to add the permision! -->
    <service
        android:name=".service.TestJobService"
        android:permission="android.permission.BIND_JOB_SERVICE"
        android:exported="true" />
  </application>
</manifest>
```

## Important caveats when running on api < 21

1. Pending jobs will be stored in `<privateappdir>/system/job/jobs.xml`. Do not
   delete this file as it will causing jobs to not run.

2. If you use `setRequiresDeviceIdle(true)` then it may not immediately run in
   the first idle window. If your app is not running, it will no longer receive
   notifications on when the device is idle, therefore, it will awake every 91
   minutes to check. This is hopefully a reasonable compromise between
   preserving battery life and ensuring your job is run.

3. There may be other subtle differences on when and how many jobs run at the
   same time. This is because, unlike the system `JobScheduler`, the current
   implementatoin cannot detect the state of other jobs running on the system
   and batch them together.

## Contributing

The best way to help out right now is by testing, run it for a while and see if
it behaves like expected. Creating apps that would help log long-term usage
would be helpful as well.

If you want to do something more specific, feel free to leave a comment on any of
the unassigned issues, or create your own.
