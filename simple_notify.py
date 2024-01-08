from flask import Flask
from flask import request
import sys
import xml.etree.ElementTree as ET

app = Flask(__name__)

@app.route('/notify', methods=['NOTIFY'])
def log():
    root = ET.fromstring(request.get_data())
    ET.indent(root)
    sys.stdout.write('Headers: {}\r\n'.format(request.headers))
    sys.stdout.flush()
    sys.stdout.write('Body: {}\r\n'.format(ET.tostring(root, encoding='unicode')))
    sys.stdout.flush()
    return '200: OK'

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)