// 담당: 역할 2 — AudioWorklet 입력을 모아 녹음 모듈로 전달
class WavCaptureProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.chunkSize = 2048;
    this.chunk = new Float32Array(this.chunkSize);
    this.offset = 0;
    this.port.onmessage = (event) => {
      if (event.data?.type === 'flush') {
        this.flush();
        this.port.postMessage({ type: 'flushed' });
      }
    };
  }

  sendChunk(length) {
    const samples = this.chunk.slice(0, length);
    this.port.postMessage({ type: 'samples', samples }, [samples.buffer]);
    this.chunk = new Float32Array(this.chunkSize);
    this.offset = 0;
  }

  flush() {
    if (this.offset > 0) this.sendChunk(this.offset);
  }

  process(inputs, outputs) {
    outputs[0]?.[0]?.fill(0);
    const input = inputs[0]?.[0];
    if (!input) return true;

    let inputOffset = 0;
    while (inputOffset < input.length) {
      const copied = Math.min(this.chunkSize - this.offset, input.length - inputOffset);
      this.chunk.set(input.subarray(inputOffset, inputOffset + copied), this.offset);
      this.offset += copied;
      inputOffset += copied;
      if (this.offset === this.chunkSize) this.sendChunk(this.chunkSize);
    }

    return true;
  }
}

registerProcessor('wontake-wav-capture', WavCaptureProcessor);
