import { LitElement, html, css } from 'lit';

class ImageUploader extends LitElement {
  static styles = css`
    :host {
      display: block;
      font-family: Arial, sans-serif;
    }
    .uploader {
      display: flex;
      flex-direction: row;
      justify-content: center;
      align-items: center;
      gap: 10px;
      border: 1px dashed #ccc;
      border-radius: 4px;
      padding: 1px;
      text-align: center;
      cursor: pointer;
    }
    .uploader:hover {
      border-color: #aaa;
    }
    input[type="file"] {
      display: none;
    }
    .thumbnail {
      width: 50px; /* Fixed width */
      height: 50px; /* Fixed height */
      object-fit: cover; /* Maintain aspect ratio, crop if necessary */
      border-radius: 8px; /* Optional: rounded corners */
      margin-top: 10px;
    }
  `;

  static properties = {
    // _imageUrl: { state: true },
    _file: { state: true }, // Holds the uploaded file
    nopreview: { attribute: 'no-preview', type: Boolean } // New reactive property
  };

  constructor() {
    super();
    this.nopreview = false; 
    this._file = null
    /** @type {string|null} */
    this._base64 = null
  }

  /**
   * @return  {string|null}  base64 string
   */
  get value() {
    return this._base64
  }
  
  get #imageUrl() {
    return this._file && URL.createObjectURL(this._file);  
  }

  render() {
    return html`
      <div class="uploader" @click=${this._openFileDialog}>
        <input type="file" @change=${this._handleFileUpload} accept="image/*">
        ${this.#imageUrl
          ? this.nopreview
            ? html`<p>Uploaded File: ${this._file.name}</p>` 
            : html`<p>Uploaded Image:</p><img class="thumbnail" src=${this.#imageUrl} alt="Uploaded image">`
          : html`<p>Click here or drag and drop an image file</p>`}
      </div>
    `;
  }

  _openFileDialog() {
    /**  @type {HTMLInputElement|null|undefined} */
    const input = this.shadowRoot?.querySelector('input[type="file"]')
    input?.click();
  }

  /**
   *
   * @param {Event} e  event
   */
  _handleFileUpload(e) {
    // @ts-ignore
    const file = e.target?.files[0];
    if (file && file.type.startsWith('image/')) {
      this._file = file; // Set the file name
      this.#convertFileToBase64(file)
        .then( base64 => this._base64 = base64 )
        .catch( error => alert(error.message) );
    } else {
      alert('Please select a valid image file.');
    }
  }

  /**
   * Convert a file to a Base64 string.
   * @param {File} file File object to convert
   * @return {Promise<string>} Promise resolving to the Base64 string
   */
  #convertFileToBase64(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result;
        
        if (typeof result === 'string') {
          // The `onload` result is a data URL, which consists of a prefix
          // (data:application/octet-stream;base64,) and the actual Base64
          // string. We split the string at the comma and take the second
          // element, which is the Base64 string
          resolve(result.split(',')[1]);
          return;
        } 
        
        if (result instanceof ArrayBuffer) {
          const buffer = new Uint8Array(result);
          const binaryString = buffer.reduce((acc, byte) => acc + String.fromCharCode(byte), '');
        // Encode the binary string to Base64
          resolve(btoa(binaryString));
          return;
        } 
        
        reject(new Error( (!result) ? 'Failed to read file' : 'Unknown type of file'));
        
      }
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }
}

customElements.define('lg4j-image-uploader', ImageUploader);