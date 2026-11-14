/**
 * Create an incremental parser for string-decoded Server-Sent Events.
 *
 * The callback receives { event, data }. JSON decoding belongs to the caller.
 */
export function createSseParser(onEvent) {
  if (typeof onEvent !== 'function') {
    throw new TypeError('onEvent must be a function')
  }

  let buffer = ''
  let eventName = ''
  let dataLines = []
  let hasData = false

  function dispatchEvent() {
    if (!hasData) {
      eventName = ''
      dataLines = []
      return
    }

    const event = {
      event: eventName || 'message',
      data: dataLines.join('\n')
    }

    eventName = ''
    dataLines = []
    hasData = false
    onEvent(event)
  }

  function processLine(line) {
    if (line === '') {
      dispatchEvent()
      return
    }

    // Lines beginning with a colon are SSE comments.
    if (line.startsWith(':')) return

    const colonIndex = line.indexOf(':')
    const field = colonIndex === -1 ? line : line.slice(0, colonIndex)
    let value = colonIndex === -1 ? '' : line.slice(colonIndex + 1)

    // SSE removes at most one ASCII space after the colon, nothing else.
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }

    if (field === 'event') {
      eventName = value
    } else if (field === 'data') {
      hasData = true
      dataLines.push(value)
    }
  }

  function processLines(isFinal) {
    let lineStart = 0

    for (let index = 0; index < buffer.length; index += 1) {
      const character = buffer[index]
      if (character !== '\n' && character !== '\r') continue

      // A trailing CR may be half of a CRLF pair arriving in the next chunk.
      if (character === '\r' && index === buffer.length - 1 && !isFinal) {
        break
      }

      processLine(buffer.slice(lineStart, index))

      if (character === '\r' && buffer[index + 1] === '\n') {
        index += 1
      }
      lineStart = index + 1
    }

    buffer = buffer.slice(lineStart)

    if (isFinal && buffer !== '') {
      processLine(buffer)
      buffer = ''
    }
  }

  return {
    push(chunk) {
      buffer += chunk
      processLines(false)
    },

    flush() {
      processLines(true)
      dispatchEvent()
    }
  }
}
