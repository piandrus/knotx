# Fragment Assembler
Fragment Assembler joins all Fragments into the final output. It's executed at the end of 
all suitable Knots processing, just before generating the response to the page visitor.

## How does it work?
Fragment Assembler reads Fragments from Knot Context, joins them all into one string (future body 
of the response), packs it back to Knot Context and returns back to the caller. 
See examples below for more details.

### How Fragments are being joined?
Lets explain process of fragments join using example. Fragment Assembler reads Knot Context having 
three Fragments:
```html
<html>
<head>
  <title>Test</title>
</head>
<body>
<h1>test</h1>
```
```html
  <h2>this is webservice no. 1</h2>
  <div>message - a</div>
```
```html
</body>
</html>
```
Fragment Assembler joins all those Fragments into one string:
```html
<html>
<head>
  <title>Test</title>
</head>
<body>
<h1>test</h1>
  <h2>this is webservice no. 1</h2>
  <div>message - a</div>
</body>
</html>
```
### How does Assembler join unprocessed Fragments?
Lets imagine that some Fragments were not processed and they still contain dynamic snippets definitions. 
It is not expected behaviour, so Fragment Assembler must handle it. There are three possible strategies 
provided: `AS_IS`, `UNWRAP`, `IGNORE`. They can be configured with entry `unprocessedStrategy`.
See Fragments below and then compare those strategies. 
```html
<html>
<head>
  <title>Test</title>
</head>
<body>
<h1>test</h1>
```
```html
<script data-knotx-knots="services,handlebars" data-knotx-service="first-service" type="text/knotx-snippet">
  <h2>{{message}}</h2>
  <div>{{body.a}}</div>
</script>
```
```html
</body>
</html>
```
#### AS_IS strategy
It leaves fragments untouched. So, result of join will look like below for our example:
```html
<html>
<head>
  <title>Test</title>
</head>
<body>
<h1>test</h1>
<script data-knotx-knots="services,handlebars" data-knotx-service="first-service" type="text/knotx-snippet">
  <h2>{{message}}</h2>
  <div>{{body.a}}</div>
</script>
</body>
</html>
```
#### UNWRAP strategy
It unwraps the snippet, by removing snippet tag tag leaving just body of the snippet. So, the result of 
join will look like this:
```html
<html>
<head>
  <title>Test</title>
</head>
<body>
<h1>test</h1>
<!-- SNIPPET UNWRAPED START -->
  <h2>{{message}}</h2>
  <div>{{body.a}}</div>
<!-- SNIPPET UNWRAPED STOP -->
</body>
</html>
```
#### IGNORE strategy
It ignores all Fragments which contains dynamic tag definitions.
```html
<html>
<head>
  <title>Test</title>
</head>
<body>
<h1>test</h1>
<!-- SNIPPET IGNORED -->
</body>
</html>
```

## How to configure?
Fragment Assembler is deployed using Vert.x service factory as a separate
[verticle](http://vertx.io/docs/apidocs/io/vertx/core/Verticle.html) and it's shipped with default 
configuration.

Default configuration shipped with the verticle as `io.knotx.FragmentAssembler.json` file available in classpath.
```json
{
  "main": "io.knotx.knot.assembler.FragmentAssemblerVerticle",
  "options": {
    "config": {
      "address": "knotx.core.assembler",
      "unprocessedStrategy": "UNWRAP"
    }
  }
}
```
In short, the default configuration just defines event bus address on which the Assembler listens
for jobs to process and strategy how to handle unprocessed snippets.

Detailed description of each configuration option is described in the next subsection.

### Fragment Assembler config

| Name                        | Type      | Mandatory      | Description  |
|-------:                     |:-------:  |:-------:       |-------|
| `address`                   | `String`  | &#10004;       | Event bus address of the Fragment Assembler verticle. |
| `unprocessedStrategy`       | `String`  | &#10004;       | Strategy for unprocessed Fragments (`AS_IS`, `UNWRAP`, `IGNORE`). `UNWRAP` is default strategy if no strategy defined. |
| `snippetTagName`            | `String`  | &#10004;       | The name of a tag that will be recognised as a Knot.x snippet. The default value is `script`. Remember to update [[Splitter configuration\|Splitter#how-to-configure]] |

**Important - when specifying `snippetTagName` remember to not use standard HTML tags like `div`, `span`, etc.
Knot.x splits an HTML into fragments by parsing it as a string to get the best possible performance. 
It simply search the text for the opening and first matching closing tag. It does not analyse the text 
as HTML. So, if you use `div` as fragmentTagName, and inside your will use multiple `div` tags too, 
then it will not pick the one that matches first opening, instead it will get the fragment up to the 
first closing `div` tag. It will result in a broken HTML structure.**
