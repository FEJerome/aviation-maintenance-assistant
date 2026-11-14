import { describe, expect, it } from 'vitest'
import { createSseParser } from './sseParser.js'

function createCollector() {
  const events = []
  const parser = createSseParser(event => events.push(event))
  return { events, parser }
}

describe('createSseParser', () => {
  it('parses token and complete events in event order', () => {
    const { events, parser } = createCollector()

    parser.push(
      'event: token\n' +
      'data: hello\n\n' +
      'event: complete\n' +
      'data: {"conversationId":"conversation-1","outcome":"ANSWERED"}\n\n'
    )
    parser.flush()

    expect(events).toEqual([
      { event: 'token', data: 'hello' },
      {
        event: 'complete',
        data: '{"conversationId":"conversation-1","outcome":"ANSWERED"}'
      }
    ])
  })

  it('does not emit before a complete event boundary arrives', () => {
    const { events, parser } = createCollector()

    parser.push('event: token\r\ndata: hel')
    expect(events).toEqual([])

    parser.push('lo\r')
    expect(events).toEqual([])

    parser.push('\n\r')
    expect(events).toEqual([])

    parser.push('\n')
    expect(events).toEqual([{ event: 'token', data: 'hello' }])
  })

  it('produces identical events for every two-chunk split', () => {
    const wire =
      'event: token\r\n' +
      'data:  leading and trailing  \r\n\r\n' +
      'event: complete\r\n' +
      'data: {"traceId":"trace-1","degraded":false}\r\n\r\n'
    const expected = [
      { event: 'token', data: ' leading and trailing  ' },
      { event: 'complete', data: '{"traceId":"trace-1","degraded":false}' }
    ]

    for (let splitAt = 0; splitAt <= wire.length; splitAt += 1) {
      const { events, parser } = createCollector()
      parser.push(wire.slice(0, splitAt))
      parser.push(wire.slice(splitAt))
      parser.flush()
      expect(events, `split at ${splitAt}`).toEqual(expected)
    }
  })

  it('handles one-character reads and mixed LF, CRLF, and CR endings', () => {
    const { events, parser } = createCollector()
    const wire =
      'event: token\rdata: first\r\r' +
      'event: token\ndata: second\n\n' +
      'event: token\r\ndata: third\r\n\r\n'

    for (const character of wire) {
      parser.push(character)
    }
    parser.flush()

    expect(events).toEqual([
      { event: 'token', data: 'first' },
      { event: 'token', data: 'second' },
      { event: 'token', data: 'third' }
    ])
  })

  it('joins multiple data lines with newlines without trimming values', () => {
    const { events, parser } = createCollector()

    parser.push(
      'event: token\n' +
      'data:  first  \n' +
      'data:second\n' +
      'data:\tthird\t\n' +
      'data:  \n\n'
    )
    parser.flush()

    expect(events).toEqual([
      { event: 'token', data: ' first  \nsecond\n\tthird\t\n ' }
    ])
  })

  it('preserves explicit empty, space-only, and tab-only token data', () => {
    const { events, parser } = createCollector()

    parser.push(
      'event: token\ndata:\n\n' +
      'event: token\ndata:  \n\n' +
      'event: token\ndata:\t\n\n'
    )
    parser.flush()

    expect(events).toEqual([
      { event: 'token', data: '' },
      { event: 'token', data: ' ' },
      { event: 'token', data: '\t' }
    ])
  })

  it('ignores empty blocks, comments, unknown fields, and events without data', () => {
    const { events, parser } = createCollector()

    parser.push(
      '\n\r\n' +
      ': keep-alive\n\n' +
      'event: token\n\n' +
      'id: event-1\nunknown: value\n\n'
    )
    parser.flush()

    expect(events).toEqual([])
  })

  it('uses message as the default event and the last event field value', () => {
    const { events, parser } = createCollector()

    parser.push(
      'data: default event\n\n' +
      'event:\ndata: empty event name\n\n' +
      'event: token\nevent: complete\ndata: last event name\n\n'
    )
    parser.flush()

    expect(events).toEqual([
      { event: 'message', data: 'default event' },
      { event: 'message', data: 'empty event name' },
      { event: 'complete', data: 'last event name' }
    ])
  })

  it('preserves complete and error JSON payloads exactly', () => {
    const { events, parser } = createCollector()
    const complete = '{"conversationId":"conversation-1","outcome":"ANSWERED","citations":[]}'
    const error = '{"code":"LLM_TIMEOUT","message":"Configured LLM endpoint timed out.","traceId":"trace-1"}'

    parser.push(
      `event: complete\ndata: ${complete}\n\n` +
      `event: error\ndata: ${error}\n\n`
    )
    parser.flush()

    expect(events).toEqual([
      { event: 'complete', data: complete },
      { event: 'error', data: error }
    ])
    expect(JSON.parse(events[0].data).outcome).toBe('ANSWERED')
    expect(JSON.parse(events[1].data).code).toBe('LLM_TIMEOUT')
  })

  it.each([
    ['an unterminated final line', 'event: token\ndata: tail', 'tail'],
    ['a final line ending without a blank line', 'event: complete\ndata: {"ok":true}\n', '{"ok":true}'],
    ['a trailing CR at EOF', 'event: token\ndata: last\r', 'last']
  ])('flushes %s exactly once at EOF', (_description, wire, expectedData) => {
    const { events, parser } = createCollector()

    parser.push(wire)
    expect(events).toEqual([])
    parser.flush()
    parser.flush()

    expect(events).toHaveLength(1)
    expect(events[0].data).toBe(expectedData)
  })

  it('does not invent an event when EOF has no pending data', () => {
    const { events, parser } = createCollector()

    parser.push(': comment without a final newline')
    parser.flush()
    parser.push('event: token')
    parser.flush()

    expect(events).toEqual([])
  })
})
