from flask import Flask
from flask import request
import sys
from xml.etree.ElementTree import ElementTree
from xml.etree.ElementTree import Element
from xml.etree.ElementTree import SubElement
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
    tree = ElementTree(root)
    with open('/Users/danielwinks/Code/Hubitat-Public/xml.xml', 'a') as f:
        f.write('\r\n')
        tree.write(f, encoding='unicode')
        f.flush()
        f.close()
    return '200: OK'

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)