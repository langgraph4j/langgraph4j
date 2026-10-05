import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import '../src/lg4j-executor';
import type { LG4JExecutorElement } from '../src/lg4j-executor';

describe('lg4j-executor AG-UI transport', () => {
  let element: LG4JExecutorElement;

  beforeEach(() => {
    element = document.createElement('lg4j-executor');
    element.agentId = 'test agent';
    document.body.append(element);
  });

  afterEach(() => {
    element.remove();
    vi.unstubAllGlobals();
  });

  it('POSTs RunAgentInput to the agent endpoint and renders every SSE event in order', async () => {
    const events = [
      { type: 'RUN_STARTED', threadId: 'thread', runId: 'run' },
      { type: 'CUSTOM', name: 'progress', value: { step: 1 } },
      { type: 'STATE_SNAPSHOT', snapshot: { answer: 42 } },
      { type: 'RUN_FINISHED', threadId: 'thread', runId: 'run' },
    ];
    const fetch = vi.fn().mockResolvedValue(new Response(
      events.map((event) => `data: ${JSON.stringify(event)}\n\n`).join(''),
      { headers: { 'Content-Type': 'text/event-stream' } },
    ));
    vi.stubGlobal('fetch', fetch);
    element.prompt = 'Hello';
    element.stateInput = '{"count":1}';
    element.execute();
    await vi.waitFor(() => expect(element.status).toBe('Completed'));
    await element.updateComplete;

    const [url, request] = fetch.mock.calls[0];
    expect(url).toBe('http://localhost:8081/stream/test%20agent');
    expect(request.method).toBe('POST');
    const input = JSON.parse(request.body);
    expect(input).toMatchObject({
      state: { count: 1 }, tools: [], context: [], forwardedProps: {},
      messages: [{ role: 'user', content: 'Hello' }],
    });
    expect(input.threadId).toBeTruthy();
    expect(input.runId).toBeTruthy();
    expect(input.messages[0].id).toBeTruthy();
    expect(element.events).toEqual(events);
    const items = element.shadowRoot!.querySelectorAll('li');
    expect(items).toHaveLength(events.length);
    expect(items[1].textContent).toContain('progress');
    expect(element.createRunInput().threadId).not.toBe(input.threadId);
  });

  it('rejects missing agent IDs and invalid state before making a request', () => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    element.agentId = '';
    element.execute();
    expect(element.error).toBe('Enter an agent ID.');
    element.agentId = 'agent';
    for (const state of ['null', '[]', '123', '{invalid']) {
      element.stateInput = state;
      element.execute();
      expect(element.status).toBe('Failed');
      expect(element.running).toBe(false);
    }
    expect(fetch).not.toHaveBeenCalled();
  });

  it('shows transport errors and releases the running state', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Server unavailable')));
    element.execute();
    await vi.waitFor(() => expect(element.running).toBe(false));
    expect(element.status).toBe('Failed');
    expect(element.error).toContain('Server unavailable');
  });

  it('retains a RUN_ERROR event and displays its message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      'data: {"type":"RUN_ERROR","message":"Workflow failed"}\n\n',
      { headers: { 'Content-Type': 'text/event-stream' } },
    )));
    element.execute();
    await vi.waitFor(() => expect(element.running).toBe(false));
    expect(element.status).toBe('Failed');
    expect(element.error).toBe('Workflow failed');
    expect(element.events).toHaveLength(1);
  });

  it.each(['cancel', 'disconnect'])('aborts an active request on %s', async (action) => {
    let signal: AbortSignal | undefined;
    const fetch = vi.fn((_url, request: RequestInit) => {
      signal = request.signal!;
      return new Promise<Response>((_resolve, reject) => {
        signal!.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      });
    });
    vi.stubGlobal('fetch', fetch);
    element.execute();
    element.execute();
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledOnce());
    if (action === 'cancel') element.cancel();
    else element.remove();
    expect(signal!.aborted).toBe(true);
    expect(element.status).toBe('Cancelled');
    expect(element.running).toBe(false);
  });
});
