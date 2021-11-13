## Thought process

okay so, my first thought was "okay this is simple, I'll just make a quartz job or a use kotlin coroutines to schedule a job, handle some failures, and I'm done".
but then as I got into writing the code, I realized why I hated every system that I used that had scheduled payments. it's REALLY HARD to make a bullet-proof system that can handle all exceptions and edge cases, and I'm not even close to handling everything that I think can go wrong.

so I'll try to break down what I did.

### Exceptions and error handling
first of, I added 2 new exceptions:
`InsufficientFundsException` and `InvoiceDoubleChargeException`.
the former was added so that I can have a single way of dealing with errors (exceptions) instead of handling some errors with an if/else blocks while handling others with a try/catch blocks, while the latter was used to expose an API that would allow me to charge any pending invoice manually.

### Scheduling
At first, I just used a simple delay, and I thought making a completely separate module for this would be over engineering. but then I wanted to also add exponential backoff for retrying when network failures occur, and so I needed to find a place to put that logic, so a new module was born.

### Invoices
as for how the invoices are created, I thought about how a "pay as you go" system would probably require more sophisticated scheduling than a "pre-paid" system, and I tried to make an option suitable for both cases, and my logic was this: every invoice is mutable, and cannot be charged more than once. the system can create invoices as it pleases, so if we needed a "pay as you go" feature, we can add the logic to create an invoice once the user racks up debt, otherwise, the system can create invoices at the beginning of each month that are pending to be paid the next month.

### what happens when an invoice fails to get paid
I didn't know what to do in this case, I thought that depends on the company policy, for example, my internet subscription gets renewed every month, but I get 7 extra days to pay for the new month (failing to pay on the 1st day of the month doesn't automatically cancel the subscription or stop the service). so it made sense to not include some logic for stopping the service if a payment failed, and I also added an end point to allow for paying for a certain invoice (I imagine this would be triggered by the user manually through some pretty-looking website/app). also, having all the errors in once place would make it easy to implement a "send notification" feature to inform the user that they need to pay their bill.

one thing that I didn't handle was, what if the application crashed in the middle of the scheduled job. this would likely require a lot of work (seems like a lot of fun to do though). But I thought of a way to have automatic failure recovery, and it goes like so:
1. add a due date for the invoice.
2. instead of scheduling ALL pending invoices to be paid, only pay the pending invoices that are past their due date.
3. when the server first starts up, pay all pending invoices that are past their due dates

this is simple, but it would still fail in case the app crashed after charging an invoice, but before committing the change to the database.
to handle that, we may use some form of intermediate state, where instead of only having `InvoiceStatus.PENDING` and `InvoiceStatus.PAID` we would also have `InvoiceStatus.IN_PROGRESS` to indicate that we are about charge the customer for that invoice. 
if on startup, some invoices were in the `InvoiceStatus.IN_PROGRESS` state, we would know that there might have been a successful attempt to charge that invoice, and so, we could maybe query the bank to check for the latest charges made by us for that customer (is that even legal?) and well, if the bank system also failed after charging this invoice but before logging it to their system, then.. I guess we're out of luck?  but in all cases, we still know that this invoice needs investigation, and there could be a lot of details on how to investigate this.

### Things I had fun learning
- working with docker: this was my first time running docker myself, usually at corporate jobs, this task is thrown to DevOps and I don't get to do all the hair-pulling that comes with building and deploying systems
- writing unit tests: I also never wrote unit tests in production enviroments, and I enjoyed it, at first it was a burden "ugh, I have to make my code unit-testable" but then it turned into "wait, will this actually work? let me write a small unit test and try it out".

### Final notes
this was really fun, I really missed coding in kotlin, and I hope I can do it more often in the future :)

----

## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus .
docker run -p 7000:7000 antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!
