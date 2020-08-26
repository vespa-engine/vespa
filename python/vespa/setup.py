import os
import setuptools


licenses = {
    "apache2": (
        "Apache Software License 2.0",
        "OSI Approved :: Apache Software License",
    ),
}
statuses = [
    "1 - Planning",
    "2 - Pre-Alpha",
    "3 - Alpha",
    "4 - Beta",
    "5 - Production/Stable",
    "6 - Mature",
    "7 - Inactive",
]
py_versions = (
    "2.0 2.1 2.2 2.3 2.4 2.5 2.6 2.7 3.0 3.1 3.2 3.3 3.4 3.5 3.6 3.7 3.8".split()
)


def get_target_version():
    build_nr = os.environ.get("GITHUB_RUN_NUMBER", "0+dev")
    version = "0.1"
    return "{}.{}".format(version, build_nr)


min_python = "3.6"

setuptools.setup(
    name="pyvespa",
    version=get_target_version(),
    description="Python API for vespa.ai",
    keywords="vespa, search engine, data science",
    author="Thiago G. Martins",
    author_email="tmartins@verizonmedia.com",
    user="vespa-engine",
    branch="master",
    license=(
        "Apache Software License 2.0",
        "OSI Approved :: Apache Software License",
    ),
    classifiers=[
        "Development Status :: " + statuses[2],
        "Intended Audience :: " + "Developers".title(),
        "License :: Apache Software License 2.0",
        "Natural Language :: " + "English".title(),
    ]
    + [
        "Programming Language :: Python :: " + o
        for o in py_versions[py_versions.index(min_python) :]
    ],
    packages=setuptools.find_packages(),
    include_package_data=True,
    install_requires=["requests", "pandas", "docker", "jinja2"],
    python_requires=">=3.6",
    long_description_content_type="text/markdown",
    zip_safe=False,
    data_files=[
        (
            "templates",
            [
                "vespa/templates/hosts.xml",
                "vespa/templates/services.xml",
                "vespa/templates/schema.txt",
            ],
        )
    ],
)
